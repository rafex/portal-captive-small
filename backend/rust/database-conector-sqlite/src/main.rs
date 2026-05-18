use rumqttc::{Client, Event, Incoming, MqttOptions, QoS};
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
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
                let response = handle_request(&conn, &payload);
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
            user_id TEXT PRIMARY KEY,
            first_name TEXT NOT NULL,
            last_name TEXT NOT NULL,
            age INTEGER,
            email TEXT UNIQUE,
            phone TEXT UNIQUE,
            mobile TEXT,
            address_text TEXT,
            social_facebook TEXT,
            social_instagram TEXT,
            social_tiktok TEXT,
            social_x TEXT,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
         );
         CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
         CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);",
    )?;
    Ok(())
}

fn extract_reply_topic(payload: &str) -> Option<String> {
    serde_json::from_str::<Request>(payload).ok().and_then(|r| r.reply_topic)
}

fn handle_request(conn: &Connection, payload: &str) -> Response {
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
        _ => err_resp(request_id, "unsupported_op".to_string()),
    }
}

fn user_save(conn: &Connection, req: &Request) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO users (
            user_id, first_name, last_name, age, email, phone, mobile, address_text,
            social_facebook, social_instagram, social_tiktok, social_x,
            password_hash, password_salt, created_at, updated_at
         ) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16)
         ON CONFLICT(user_id) DO UPDATE SET
            first_name=excluded.first_name,
            last_name=excluded.last_name,
            age=excluded.age,
            email=excluded.email,
            phone=excluded.phone,
            mobile=excluded.mobile,
            address_text=excluded.address_text,
            social_facebook=excluded.social_facebook,
            social_instagram=excluded.social_instagram,
            social_tiktok=excluded.social_tiktok,
            social_x=excluded.social_x,
            password_hash=excluded.password_hash,
            password_salt=excluded.password_salt,
            updated_at=excluded.updated_at",
        params![
            req.user_id,
            req.first_name,
            req.last_name,
            req.age,
            req.email,
            req.phone,
            req.mobile,
            req.address,
            req.social_facebook,
            req.social_instagram,
            req.social_tiktok,
            req.social_x,
            req.password_hash,
            req.password_salt,
            req.created_at,
            req.updated_at,
        ],
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

    let sql = if field == "email" {
        "SELECT user_id, first_name, last_name, age, email, phone, mobile, address_text, social_facebook, social_instagram, social_tiktok, social_x, password_hash, password_salt, created_at, updated_at FROM users WHERE email=?1 LIMIT 1"
    } else {
        "SELECT user_id, first_name, last_name, age, email, phone, mobile, address_text, social_facebook, social_instagram, social_tiktok, social_x, password_hash, password_salt, created_at, updated_at FROM users WHERE phone=?1 LIMIT 1"
    };

    let mut stmt = conn.prepare(sql)?;
    let mut rows = stmt.query(params![v])?;

    if let Some(row) = rows.next()? {
        return Ok(Response {
            request_id,
            status: "ok".to_string(),
            error: None,
            found: Some(true),
            user_id: Some(row.get::<_, String>(0)?),
            first_name: Some(row.get::<_, String>(1)?),
            last_name: Some(row.get::<_, String>(2)?),
            age: row.get::<_, Option<i64>>(3)?,
            email: row.get::<_, Option<String>>(4)?,
            phone: row.get::<_, Option<String>>(5)?,
            mobile: row.get::<_, Option<String>>(6)?,
            address: row.get::<_, Option<String>>(7)?,
            social_facebook: row.get::<_, Option<String>>(8)?,
            social_instagram: row.get::<_, Option<String>>(9)?,
            social_tiktok: row.get::<_, Option<String>>(10)?,
            social_x: row.get::<_, Option<String>>(11)?,
            password_hash: Some(row.get::<_, String>(12)?),
            password_salt: Some(row.get::<_, String>(13)?),
            created_at: Some(row.get::<_, String>(14)?),
            updated_at: Some(row.get::<_, String>(15)?),
        });
    }

    Ok(Response {
        request_id,
        status: "ok".to_string(),
        error: None,
        found: Some(false),
        user_id: None,
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

fn ok_resp(request_id: String) -> Response {
    Response {
        request_id,
        status: "ok".to_string(),
        error: None,
        found: None,
        user_id: None,
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
