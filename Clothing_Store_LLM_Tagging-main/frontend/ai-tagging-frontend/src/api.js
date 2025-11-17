
const API_BASE = "http://localhost:8080";
//checks if responce is in JSON format then the reponce gets parsed
async function getJSON(res) {

  const text = await res.text();

  const ct = res.headers.get("content-type") || "";

  if (!ct.includes("application/json")) {

    throw new Error(`${res.status} ${res.statusText} – expected JSON, got ${ct || "unknown"}.\n${text.slice(0,200)}`);

  } try { return JSON.parse(text);

   }
  catch (e) { throw new Error(`Bad JSON: ${e.message}\n${text.slice(0,200)}`); }
}



//posts from backend are fetched, pagination is then applied and finally a portion of the results is returned
export async function searchPostsPaged(query, mode, page = 1, pageSize = 12, opts = {}) {

  const MAX = 10000; 
  const params = new URLSearchParams();
  if (query) params.set("q", query);
  if (mode)  params.set("mode", mode);
  params.set("limit", String(MAX)); 


  const res = await fetch(`${API_BASE}/search?${params}`, {
    headers: { Accept: "application/json" },
    signal: opts.signal,
  });
  const data = await getJSON(res);

  if (Array.isArray(data)) {
    const total = data.length;
    const start = (page - 1) * pageSize;
    const end   = start + pageSize;
    return { items: data.slice(start, end), total };
  }

  const rows  = data.items ?? [];
  const total = data.total ?? rows.length;
  const start = (page - 1) * pageSize;
  const end   = start + pageSize;
  return { items: rows.slice(start, end), total };
}


//Uploads image to backennd, then the images meta data is returned.
export async function uploadImage(file) {

  const fd = new FormData();
  fd.append("file", file);
  const res = await fetch(`${API_BASE}/images/upload`, { method: "POST", body: fd });
  const data = await getJSON(res);
  if (!res.ok) {
    const msg = data?.message || JSON.stringify(data);
    throw new Error(`Upload failed: ${msg}`);
  }
  return data;
}



//compresses image to a specific specific size before the image is uploaded, this is done to inncrease website performance
export async function compressImage(file, { maxW = 1280, maxH = 1280, quality = 0.78 } = {}) {
  const bmp = await createImageBitmap(file);
  const scale = Math.min(1, maxW / bmp.width, maxH / bmp.height);
  const width = Math.round(bmp.width * scale);
  const height = Math.round(bmp.height * scale);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;

  canvas.getContext("2d").drawImage(bmp, 0, 0, width, height);


  const blob = await new Promise(res => canvas.toBlob(res, "image/jpeg", quality));
  return new File([blob], file.name.replace(/\.\w+$/, ".jpg"), { type: "image/jpeg" });
}

