const stopWords = new Set(["a","an","and","the","of","on","in","to","as","with","for","by"]);


export const clean = (arr) =>

  Array.from(new Set(
    arr.map(s => s.trim().toLowerCase()).filter(s => s && s.length > 1 && !stopWords.has(s))

  )).slice(0, 12);

export const splitTroughComma = (str) => clean((str || "").split(","));

export const splitWords = (str) => clean((str || "").split(/[,\s]+/));

export const makeTags = (hit, mode) =>

  mode === "alt" ? splitWords(hit?.altText || "") : splitTroughComma(hit?.llmTags || "");
