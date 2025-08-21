$(function(){

  // 全チェック
  $('#checkbox-all').click(function(){
    allCheck('#file-table', this);
  });

    $('#file-table').DataTable({
      order: [3, 'desc'],
      columnDefs: [
      ],
      lengthMenu: [ [10, 20, 30, 40, 50, -1], [10, 20, 30, 40, 50, 'ALL'] ],
      autoWidth: false,
      searching: false,
      language: l
    });

});

//window.onload = function unUsable() {
//	const dropdown = document.getElementById("place");
//	dropdown.disabled = true;
//}