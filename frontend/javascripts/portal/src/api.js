export async function postJson(baseUrl, path, payload, fetchImpl = fetch) {
  const response = await fetchImpl(`${baseUrl}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  let body = {};
  try {
    body = await response.json();
  } catch (_) {
    body = {};
  }

  if (!response.ok) {
    throw new Error(body.error || `http_${response.status}`);
  }

  return body;
}
