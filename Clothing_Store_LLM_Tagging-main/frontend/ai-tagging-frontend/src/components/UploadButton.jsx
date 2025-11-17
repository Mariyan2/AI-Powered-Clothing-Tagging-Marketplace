import { useState } from "react";
import { uploadImage, compressImage } from "../api.js";
//compresses and uploads an image. Has error handling and returns result trough onAfterUpload

export default function UploadButton({ onAfterUpload }) {
  const [loadingImage, setLoadingImage] = useState(false);
  const [error, setError] = useState("");

  async function onUpload(e) {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setLoadingImage(true);
      setError("");
      const compressed = await compressImage(file);
      const res = await uploadImage(compressed);


      setLoadingImage(false);
      try {
        onAfterUpload?.({ ...res, isNew: true });
      } catch (cbErr) {
        setError(String(cbErr?.message || cbErr));  }



    } catch (err2) {
      setError(String(err2?.message || err2));
      setLoadingImage(false);
    } finally {
      e.target.value = ""; }  }

  return (
    <div className="upload-wrap" aria-busy={loadingImage}>
      <label className={`upload-button ${loadingImage ? "cursor-wait" : ""}`}>
        {loadingImage ? "Uploading..." : "Upload"}
        <input
          type="file"
          accept="image/*"
          onChange={onUpload}
          disabled={loadingImage}
        />
      </label>

      {error && (
        <div className="error" aria-live="polite">
          {error}
        </div>
      )}
    </div>
  );
}


