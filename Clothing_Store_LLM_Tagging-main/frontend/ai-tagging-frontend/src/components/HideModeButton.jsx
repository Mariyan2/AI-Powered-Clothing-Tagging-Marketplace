
export default function HideModeButton({ show, onToggle, icon }) {
  const lock = icon ?? (show ? "🔓" : "🔒");
// goggles the visability of the LLM/Alt-text modes of tagging trough hide button switches
  return ( <button
     
    type="button"
      onClick={onToggle}
      className={`hide-btn ${show ? "" : "is-active"}`}
      aria-pressed={!show}
      aria-label={show ? "Hide labels" : "Show labels"}
      title={show ? "Hide labels" : "Show labels"}
    >

      <span className="hide-btn__icon" aria-hidden="true">{lock}</span>
    </button>
  );
}