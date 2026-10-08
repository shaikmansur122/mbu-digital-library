// "Mark all present / absent" shortcuts on the attendance form.
document.querySelectorAll('.mark-all').forEach(function (button) {
  button.addEventListener('click', function () {
    var status = button.dataset.status;
    document.querySelectorAll('#markForm input[type="radio"][value="' + status + '"]').forEach(function (radio) {
      radio.checked = true;
    });
  });
});
