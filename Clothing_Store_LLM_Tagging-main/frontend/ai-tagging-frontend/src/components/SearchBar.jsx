//simple search bar that stores input text in value then changes the state with onchange
export default function SearchBar({ value, onChange }) {
  return (
    <div className="searchbar">
      <input
        className="searchbar__input"
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label="Search"
      />
    </div>
  );
}
