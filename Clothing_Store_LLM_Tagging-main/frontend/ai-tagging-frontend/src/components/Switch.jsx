//A switch that changes between the two tagging state, LLM and Alt-text. When hidden labels are blurred 
export default function Switch({ mode = "llm", onChange, showLabels = true }) {
  const isLLMSwitched = mode === "llm";
  const toggle = () => onChange(isLLMSwitched ? "alt" : "llm");

  return (
    <button
      type="button"
      className={`mode-switch ${isLLMSwitched ? "is-llm" : ""} ${showLabels ? "" : "labels-blur"}`}
      role="switch"
      aria-checked={isLLMSwitched}
      aria-label="toggle mode of tagging"
      onClick={toggle}
      data-anonymous={!showLabels}
    >
      <span className={`label left ${!isLLMSwitched ? "active" : ""}`}>ALT</span>
      <span className={`label right ${isLLMSwitched ? "active" : ""}`}>LLM</span>
      <span className="thumb" />
    </button>
  );
}
