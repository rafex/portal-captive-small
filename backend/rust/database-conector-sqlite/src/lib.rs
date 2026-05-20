use rusqlite::Connection;
use std::ffi::CString;
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn sqlite_connector_healthcheck() -> *mut c_char {
    let status = Connection::open_in_memory()
        .map(|_| "ok")
        .unwrap_or("error");
    CString::new(status).unwrap().into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sqlite_healthcheck_returns_non_null() {
        let ptr = sqlite_connector_healthcheck();
        assert!(!ptr.is_null());
    }
}
