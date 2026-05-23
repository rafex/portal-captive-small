use rumqttc::{Client, Event, Incoming, MqttOptions, QoS};
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::env;
use std::thread;
use std::time::Duration;

#[derive(Debug, Deserialize)]
struct Request {
    #[serde(rename = "requestId")]
    request_id: Option<String>,
    op: String,
    #[serde(rename = "replyTopic")]
    reply_topic: Option<String>,
    #[serde(rename = "userId")]
    user_id: Option<String>,
    template: Option<String>,
    #[serde(rename = "deviceIp")]
    device_ip: Option<String>,
    #[serde(rename = "deviceUuid")]
    device_uuid: Option<String>,
    #[serde(rename = "deviceFingerprint")]
    device_fingerprint: Option<String>,
    #[serde(rename = "userAgent")]
    user_agent: Option<String>,
    #[serde(rename = "firstName")]
    first_name: Option<String>,
    #[serde(rename = "lastName")]
    last_name: Option<String>,
    age: Option<i64>,
    email: Option<String>,
    phone: Option<String>,
    mobile: Option<String>,
    address: Option<String>,
    #[serde(rename = "socialFacebook")]
    social_facebook: Option<String>,
    #[serde(rename = "socialInstagram")]
    social_instagram: Option<String>,
    #[serde(rename = "socialTiktok")]
    social_tiktok: Option<String>,
    #[serde(rename = "socialX")]
    social_x: Option<String>,
    #[serde(rename = "passwordHash")]
    password_hash: Option<String>,
    #[serde(rename = "passwordSalt")]
    password_salt: Option<String>,
    #[serde(rename = "createdAt")]
    created_at: Option<String>,
    #[serde(rename = "updatedAt")]
    updated_at: Option<String>,
}

#[derive(Debug, Serialize)]
struct Response {
    #[serde(rename = "requestId")]
    request_id: String,
    status: String,
    error: Option<String>,
    found: Option<bool>,
    #[serde(rename = "userId")]
    user_id: Option<String>,
    template: Option<String>,
    #[serde(rename = "deviceIp")]
    device_ip: Option<String>,
    #[serde(rename = "deviceUuid")]
    device_uuid: Option<String>,
    #[serde(rename = "deviceFingerprint")]
    device_fingerprint: Option<String>,
    #[serde(rename = "userAgent")]
    user_agent: Option<String>,
    #[serde(rename = "firstName")]
    first_name: Option<String>,
    #[serde(rename = "lastName")]
    last_name: Option<String>,
    age: Option<i64>,
    email: Option<String>,
    phone: Option<String>,
    mobile: Option<String>,
    address: Option<String>,
    #[serde(rename = "socialFacebook")]
    social_facebook: Option<String>,
    #[serde(rename = "socialInstagram")]
    social_instagram: Option<String>,
    #[serde(rename = "socialTiktok")]
    social_tiktok: Option<String>,
    #[serde(rename = "socialX")]
    social_x: Option<String>,
    #[serde(rename = "passwordHash")]
    password_hash: Option<String>,
    #[serde(rename = "passwordSalt")]
    password_salt: Option<String>,
    #[serde(rename = "createdAt")]
    created_at: Option<String>,
    #[serde(rename = "updatedAt")]
    updated_at: Option<String>,
}

fn main() {
    let mqtt_host = env::var("MQTT_HOST").unwrap_or_else(|_| "127.0.0.1".to_string());
    let mqtt_port = env::var("MQTT_PORT").ok().and_then(|v| v.parse::<u16>().ok()).unwrap_or(1883);
    let request_topic = env::var("DB_USER_REQUEST_TOPIC").unwrap_or_else(|_| "portal/db/user/request".to_string());
    let db_path = env::var("SQLITE_DB_PATH").unwrap_or_else(|_| "data/auth-service.db".to_string());
    let users_ttl_seconds = env::var("DB_USER_TTL_SECONDS")
        .ok()
        .and_then(|v| v.parse::<i64>().ok())
        .unwrap_or(3600);

    let conn = Connection::open(&db_path).expect("sqlite open failed");
    setup_sqlite(&conn).expect("sqlite setup failed");

    let mut mqtt_options = MqttOptions::new("db-mqtt-worker", mqtt_host, mqtt_port);
    mqtt_options.set_keep_alive(Duration::from_secs(30));

    let (client, mut connection) = Client::new(mqtt_options, 100);
    client
        .subscribe(request_topic.clone(), QoS::AtLeastOnce)
        .expect("subscribe failed");

    println!("db-mqtt-worker listening topic={}", request_topic);

    for event in connection.iter() {
        match event {
            Ok(Event::Incoming(Incoming::Publish(msg))) => {
                let payload = String::from_utf8_lossy(&msg.payload);
                let response = handle_request(&conn, &payload, users_ttl_seconds);
                let reply_topic = extract_reply_topic(&payload)
                    .unwrap_or_else(|| "portal/db/user/response/default".to_string());
                let body = serde_json::to_string(&response).unwrap_or_else(|_| "{\"status\":\"error\"}".to_string());
                if let Err(e) = client.publish(reply_topic, QoS::AtLeastOnce, false, body) {
                    eprintln!("publish response failed: {e}");
                }
            }
            Ok(_) => {}
            Err(e) => {
                eprintln!("mqtt event error: {e}");
                thread::sleep(Duration::from_millis(500));
            }
        }
    }
}

fn setup_sqlite(conn: &Connection) -> rusqlite::Result<()> {
    conn.execute_batch(
        "PRAGMA journal_mode=WAL;
         PRAGMA synchronous=NORMAL;
         PRAGMA busy_timeout=5000;
         PRAGMA foreign_keys=ON;",
    )?;

    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            password_hash TEXT,
            password_salt TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
         );
         CREATE TABLE IF NOT EXISTS user_profiles (
            user_id TEXT PRIMARY KEY,
            profile_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
         );",
    )?;
    Ok(())
}

fn extract_reply_topic(payload: &str) -> Option<String> {
    serde_json::from_str::<Request>(payload).ok().and_then(|r| r.reply_topic)
}

fn handle_request(conn: &Connection, payload: &str, users_ttl_seconds: i64) -> Response {
    let _ = cleanup_expired_users(conn, users_ttl_seconds);
    let req = match serde_json::from_str::<Request>(payload) {
        Ok(v) => v,
        Err(_) => return err_resp("unknown".to_string(), "invalid_json".to_string()),
    };

    let request_id = req.request_id.clone().unwrap_or_else(|| "unknown".to_string());

    match req.op.as_str() {
        "user_save" => match user_save(conn, &req) {
            Ok(_) => ok_resp(request_id),
            Err(e) => err_resp(request_id, format!("save_failed:{e}")),
        },
        "user_find_email" => match user_find(conn, "email", req.email.as_deref(), request_id.clone()) {
            Ok(resp) => resp,
            Err(e) => err_resp(request_id, format!("find_email_failed:{e}")),
        },
        "user_find_phone" => match user_find(conn, "phone", req.phone.as_deref(), request_id.clone()) {
            Ok(resp) => resp,
            Err(e) => err_resp(request_id, format!("find_phone_failed:{e}")),
        },
        "user_find_device_ip" => match user_find(conn, "device_ip", req.device_ip.as_deref(), request_id.clone()) {
            Ok(resp) => resp,
            Err(e) => err_resp(request_id, format!("find_device_ip_failed:{e}")),
        },
        _ => err_resp(request_id, "unsupported_op".to_string()),
    }
}

fn user_save(conn: &Connection, req: &Request) -> rusqlite::Result<()> {
    let profile_json = json!({
        "template": req.template.clone(),
        "deviceIp": req.device_ip.clone(),
        "deviceUuid": req.device_uuid.clone(),
        "deviceFingerprint": req.device_fingerprint.clone(),
        "userAgent": req.user_agent.clone(),
        "firstName": req.first_name.clone(),
        "lastName": req.last_name.clone(),
        "age": req.age,
        "email": req.email.clone(),
        "phone": req.phone.clone(),
        "mobile": req.mobile.clone(),
        "address": req.address.clone(),
        "socialFacebook": req.social_facebook.clone(),
        "socialInstagram": req.social_instagram.clone(),
        "socialTiktok": req.social_tiktok.clone(),
        "socialX": req.social_x.clone()
    })
    .to_string();

    conn.execute(
        "INSERT INTO users (
            id, password_hash, password_salt, created_at, updated_at
         ) VALUES (?1,?2,?3,?4,?5)
         ON CONFLICT(id) DO UPDATE SET
            password_hash=excluded.password_hash,
            password_salt=excluded.password_salt,
            updated_at=excluded.updated_at",
        params![
            req.user_id,
            req.password_hash,
            req.password_salt,
            req.created_at,
            req.updated_at,
        ],
    )?;

    conn.execute(
        "INSERT INTO user_profiles (user_id, profile_json, created_at, updated_at) VALUES (?1,?2,?3,?4)
         ON CONFLICT(user_id) DO UPDATE SET profile_json=excluded.profile_json,updated_at=excluded.updated_at",
        params![req.user_id, profile_json, req.created_at, req.updated_at],
    )?;
    Ok(())
}

fn user_find(conn: &Connection, field: &str, value: Option<&str>, request_id: String) -> rusqlite::Result<Response> {
    let v = value.unwrap_or("");
    if v.is_empty() {
        return Ok(Response {
            request_id,
            status: "ok".to_string(),
            error: None,
            found: Some(false),
            user_id: None,
            template: None,
            device_ip: None,
            device_uuid: None,
            device_fingerprint: None,
            user_agent: None,
            first_name: None,
            last_name: None,
            age: None,
            email: None,
            phone: None,
            mobile: None,
            address: None,
            social_facebook: None,
            social_instagram: None,
            social_tiktok: None,
            social_x: None,
            password_hash: None,
            password_salt: None,
            created_at: None,
            updated_at: None,
        });
    }

    let sql = match field {
        "email" => "SELECT u.id, u.password_hash, u.password_salt, u.created_at, u.updated_at, p.profile_json
         FROM users u
         LEFT JOIN user_profiles p ON p.user_id=u.id
         WHERE json_extract(p.profile_json, '$.email')=?1 LIMIT 1",
        "device_ip" => "SELECT u.id, u.password_hash, u.password_salt, u.created_at, u.updated_at, p.profile_json
         FROM users u
         LEFT JOIN user_profiles p ON p.user_id=u.id
         WHERE json_extract(p.profile_json, '$.deviceIp')=?1 LIMIT 1",
        _ => "SELECT u.id, u.password_hash, u.password_salt, u.created_at, u.updated_at, p.profile_json
         FROM users u
         LEFT JOIN user_profiles p ON p.user_id=u.id
         WHERE json_extract(p.profile_json, '$.phone')=?1 LIMIT 1",
    };

    let mut stmt = conn.prepare(sql)?;
    let mut rows = stmt.query(params![v])?;

    if let Some(row) = rows.next()? {
        let profile_raw: Option<String> = row.get(5)?;
        let profile: serde_json::Value = profile_raw
            .as_deref()
            .and_then(|s| serde_json::from_str(s).ok())
            .unwrap_or_else(|| json!({}));
        return Ok(Response {
            request_id,
            status: "ok".to_string(),
            error: None,
            found: Some(true),
            user_id: Some(row.get::<_, String>(0)?),
            template: profile.get("template").and_then(|v| v.as_str()).map(|s| s.to_string()),
            device_ip: profile.get("deviceIp").and_then(|v| v.as_str()).map(|s| s.to_string()),
            device_uuid: profile.get("deviceUuid").and_then(|v| v.as_str()).map(|s| s.to_string()),
            device_fingerprint: profile.get("deviceFingerprint").and_then(|v| v.as_str()).map(|s| s.to_string()),
            user_agent: profile.get("userAgent").and_then(|v| v.as_str()).map(|s| s.to_string()),
            first_name: profile.get("firstName").and_then(|v| v.as_str()).map(|s| s.to_string()),
            last_name: profile.get("lastName").and_then(|v| v.as_str()).map(|s| s.to_string()),
            age: profile.get("age").and_then(|v| v.as_i64()),
            email: profile.get("email").and_then(|v| v.as_str()).map(|s| s.to_string()),
            phone: profile.get("phone").and_then(|v| v.as_str()).map(|s| s.to_string()),
            mobile: profile.get("mobile").and_then(|v| v.as_str()).map(|s| s.to_string()),
            address: profile.get("address").and_then(|v| v.as_str()).map(|s| s.to_string()),
            social_facebook: profile.get("socialFacebook").and_then(|v| v.as_str()).map(|s| s.to_string()),
            social_instagram: profile.get("socialInstagram").and_then(|v| v.as_str()).map(|s| s.to_string()),
            social_tiktok: profile.get("socialTiktok").and_then(|v| v.as_str()).map(|s| s.to_string()),
            social_x: profile.get("socialX").and_then(|v| v.as_str()).map(|s| s.to_string()),
            password_hash: Some(row.get::<_, String>(1)?),
            password_salt: Some(row.get::<_, String>(2)?),
            created_at: Some(row.get::<_, String>(3)?),
            updated_at: Some(row.get::<_, String>(4)?),
        });
    }

    Ok(Response {
        request_id,
        status: "ok".to_string(),
        error: None,
        found: Some(false),
        user_id: None,
        template: None,
        device_ip: None,
        device_uuid: None,
        device_fingerprint: None,
        user_agent: None,
        first_name: None,
        last_name: None,
        age: None,
        email: None,
        phone: None,
        mobile: None,
        address: None,
        social_facebook: None,
        social_instagram: None,
        social_tiktok: None,
        social_x: None,
        password_hash: None,
        password_salt: None,
        created_at: None,
        updated_at: None,
    })
}

fn cleanup_expired_users(conn: &Connection, ttl_seconds: i64) -> rusqlite::Result<()> {
    if ttl_seconds <= 0 {
        return Ok(());
    }
    conn.execute(
        "DELETE FROM users WHERE updated_at < datetime('now', '-' || ?1 || ' seconds')",
        params![ttl_seconds],
    )?;
    Ok(())
}

fn ok_resp(request_id: String) -> Response {
    Response {
        request_id,
        status: "ok".to_string(),
        error: None,
        found: None,
        user_id: None,
        template: None,
        device_ip: None,
        device_uuid: None,
        device_fingerprint: None,
        user_agent: None,
        first_name: None,
        last_name: None,
        age: None,
        email: None,
        phone: None,
        mobile: None,
        address: None,
        social_facebook: None,
        social_instagram: None,
        social_tiktok: None,
        social_x: None,
        password_hash: None,
        password_salt: None,
        created_at: None,
        updated_at: None,
    }
}

fn err_resp(request_id: String, err: String) -> Response {
    Response {
        request_id,
        status: "error".to_string(),
        error: Some(err),
        found: None,
        user_id: None,
        template: None,
        device_ip: None,
        device_uuid: None,
        device_fingerprint: None,
        user_agent: None,
        first_name: None,
        last_name: None,
        age: None,
        email: None,
        phone: None,
        mobile: None,
        address: None,
        social_facebook: None,
        social_instagram: None,
        social_tiktok: None,
        social_x: None,
        password_hash: None,
        password_salt: None,
        created_at: None,
        updated_at: None,
    }
}


#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn setup_sqlite_enables_wal() {
        let conn = Connection::open_in_memory().expect("open");
        setup_sqlite(&conn).expect("setup");

        let mode: String = conn.query_row("PRAGMA journal_mode;", [], |r| r.get(0)).expect("journal_mode");
        assert_eq!(mode.to_lowercase(), "memory");

        let fk: i64 = conn.query_row("PRAGMA foreign_keys;", [], |r| r.get(0)).expect("fk");
        assert_eq!(fk, 1);
    }
}
