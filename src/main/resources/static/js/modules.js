// Shows the PDF file input or the YouTube link input depending on the chosen type.
document.querySelectorAll('.material-form').forEach(function (form) {
  var select = form.querySelector('.type-select');
  var pdf = form.querySelector('.for-pdf');
  var video = form.querySelector('.for-video');

  function update() {
    var isVideo = select.value === 'VIDEO';
    pdf.hidden = isVideo;
    video.hidden = !isVideo;
  }

  select.addEventListener('change', update);
  update();
});
