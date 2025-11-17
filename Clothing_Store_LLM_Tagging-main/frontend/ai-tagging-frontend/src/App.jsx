import React, { useCallback, useEffect, useState } from "react";
import "./App.css";

import Post from "./components/Post.jsx";
import SearchBar from "./components/SearchBar.jsx";
import Switch from "./components/Switch.jsx";
import HideModeButton from "./components/HideModeButton.jsx";
import UploadButton from "./components/UploadButton.jsx";
import Pager from "./components/PageNavigation.jsx";

import usePagedSearch from "./scripts/usePageSearch.js";
import { makeTags } from "./scripts/tagManager.js";

export default function App() {
  const [query, setQuery] = useState("");
  const [mode, setMode] = useState("llm");
  const [showLabels, setShowLabels] = useState(true);
  const [page, setPage] = useState(1);
  const [notice, setNotice] = useState("");

  const pageSize = 12;

  // when query or mode is cahnged set back to first page
  useEffect(() => { setPage(1); }, [query, mode]);

  // backend responce mapped to frontend
  const mapRow = useCallback(
    (d) => ({ id: d.postID, title: d.title, imageUrl: d.imageURL, tags: makeTags(d, mode), }),
    
    [mode]
  );

  const { items, total, loading } = usePagedSearch({
    query, mode, page, pageSize, mapRow
  });

  const pageCount = Math.max(1, Math.ceil((total || 0) / pageSize));

  // search query updated when tag is clicked
  function handleTagClick(tag) {
    setQuery(tag);
    setPage(1);
  }

  const [newPosts, setNewPosts] = useState([]);

  // mew image upload handler
  function handleAfterUpload(res) {
    if (!res) return;
    const mapped = {
      id: res.id,
      title: res.title,
      imageUrl: res.imageURL,
      _llmTags: res.llmTags || "",
      _altText: res.altText || "",
    };
    const tagsNow = makeTags({ llmTags: mapped._llmTags, altText: mapped._altText }, mode);
    const toAdd = { ...mapped, tags: tagsNow };

    setNewPosts(prev => {
      const next = [toAdd, ...prev];
      return next.filter((x, i, arr) => arr.findIndex(y => y.id === x.id) === i);
    });

    setPage(1);
    setNotice("Uploaded!");
    setTimeout(() => setNotice(""), 1200);
  }

  // tags recalcualation
  useEffect(() => {
    setNewPosts(prev =>
      prev.map(p => ({
        ...p,
        tags: makeTags({ llmTags: p._llmTags, altText: p._altText }, mode),
      }))
    );
  }, [mode]);

  // new posts are merged at top of the page
  const showNew = page === 1 && !query.trim();
  const rows = showNew
    ? [...newPosts, ...items].filter((x, i, arr) => arr.findIndex(y => y.id === x.id) === i) : items;



  return (
    <div className="app">
      <div className="toolbar">
        <SearchBar value={query} onChange={setQuery} />

        <Switch mode={mode} onChange={setMode} showLabels={showLabels} />

        <HideModeButton show={showLabels} onToggle={() => setShowLabels(s => !s)} />

        <UploadButton onAfterUpload={handleAfterUpload} /> 
      </div>

      {loading && <div className="status">Loading…</div>}
      {notice && !loading && <div className="status">{notice}</div>}

      <div className="grid">
        {rows.map(p => (
          <Post
            key={p.id}
            title={p.title}
            imageUrl={p.imageUrl}
            tags={p.tags}
            onTagClick={handleTagClick}
          />
        ))}
        
        {Array.from({ length: Math.max(0, 6 - rows.length) }).map((_, i) => (
          <div key={`empty-${i}`} className="post post--empty" />
        ))}
      </div>

      <Pager page={page} pageCount={pageCount} onPage={setPage} />
    </div>
  );
}
