
//navigates between using arrows
const PageNavigation = ({ page, pageCount, onPage }) => {
  
    if (pageCount <= 1) return null;
  return (
    <nav className="pager">
      <button disabled={page === 1} onClick={() => onPage(page - 1)} >
        ◀
      </button>


      <span>Page {page} / {pageCount}</span>
        <button disabled={page === pageCount} onClick= {() => onPage(page + 1) }>
       ▶
        </button>
    </nav>
  );

};


export default PageNavigation;  
