import { useEffect, useState } from "react";
import { searchPostsPaged } from "../api.js";

// Custom hook to fetch paged posts with loading/error state
export default function usePageSearch({ query, mode, page, pageSize, mapRow }) {
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  useEffect(() => {
    const ac = new AbortController();

    (async () => {
      try {
        setLoading(true);
        setErr("");

        const { items: rows = [], total: t } = await searchPostsPaged(
          (query ?? "").trim() || "*", mode, page, pageSize, { signal: ac.signal } );
        setItems(rows.map(mapRow));

        setTotal(typeof t === "number" ? t : rows.length);
      }
       catch (e) {
        if (e?.name !== "AbortError") setErr(String(e?.message || e));
      }
       finally {
        setLoading(false);
      }
    })();

    return () => ac.abort();
  }, [query, mode, page, pageSize, mapRow]);

  return { items, total, loading, err };
}
