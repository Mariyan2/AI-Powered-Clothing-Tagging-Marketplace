function deriveTitle(title, imageUrl) {
  if (title?.trim()) return title.trim();

  if (imageUrl) {
    const filename = imageUrl.split("/").pop() || "";
    return filename.replace(/\.[a-z0-9]+$/i, "") || "Untitled";
  }
  return "Untitled";
  
}

export default function Post({ title, imageUrl, tags = [], onTagClick }) {
  const safeTitle = deriveTitle(title, imageUrl);

  return (
    <div
      className="post"
      style={{ contentVisibility: "auto", containIntrinsicSize: "300px 240px" }}
    >
      <div className="post__title">{safeTitle}</div>

      <div className="post__imageWrap">
        <img
          className="post__image"
          src={imageUrl}
          alt={safeTitle}
          loading="lazy"
          fetchPriority="low"
        />
      </div>

      <div className="post__tags">
        {tags.map((t) => (
          <button
            key={t}
            type="button"
            className="tag tag--clickable"
            title={`Search for "${t}"`}
            aria-label={`Search for ${t}`}
            onClick={() => onTagClick?.(t)}
          >
            {t}
          </button>
        ))}
      </div>
    </div>
  );
}
