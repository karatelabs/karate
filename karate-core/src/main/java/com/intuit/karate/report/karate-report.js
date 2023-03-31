window.onload = function () {
  const parentIds = {}

  document.querySelectorAll('[data-parent]').forEach(child => {
    parentIds[child.dataset.parent] = true
    child.style.display = 'none'
  })

  document.querySelectorAll('div.step-container').forEach(parent => {
    if (!parentIds[parent.id]) return
    const link = document.createElement('a')
    link.dataset.stepId = parent.id
    link.href = 'javascript:void(0)'
    parent.parentNode.insertBefore(link, parent)
    link.appendChild(parent)
  })

  $('#content').on('click', 'a[data-step-id]', function () {
    const parentId = $(this).data('step-id')
    $(`[data-parent='${parentId}']`).toggle('fast')
    $(`[data-parent='${parentId}'] [data-deferred]`).each((i, deferred) => {
      const script = document.createElement('script')
      script.type = 'text/javascript'
      script.src = deferred.dataset.src
      deferred.parentNode.replaceChild(script, deferred)
    })
  })

  $("table.features-table").tablesorter();
  $("table.tags-table").tablesorter();
};

function newDiffUI(targetElement, diffResult, diffConfig, onShowRebase, onShowConfig) {
  diffConfig = diffConfig || {}
  diffConfig.tolerances = diffConfig.tolerances || {}

  // setup common vars
  const $el = $('<div class="diff-ui"></div>').appendTo($(targetElement).parent());
  const ignoredBoxes = (diffConfig.ignoredBoxes || []).map((ignoredBox, i) => Object.assign({ id: i }, ignoredBox))
  const tolerances = ['red', 'green', 'blue', 'alpha', 'minBrightness', 'maxBrightness'].reduce((tols, toleranceName) => {
    if (diffConfig.tolerances[toleranceName] !== undefined) tols[toleranceName] = diffConfig.tolerances[optionName]
    return tols
  }, {})
  let firstPaintComplete = false
  let resembleControl

  // add UI dynamically to avoid repeating html on-disk for each screenshot
  createHtml()

  // create baseline and latest images
  $el.find('.baseline').append($('<img/>').attr('src', diffResult.baseline))
  $el.find('.img-container').append($('<img class="hidden"/>').attr('src', diffResult.baseline))
  $el.find('.latest').append($('<img/>').attr('src', diffResult.latest))
  $el.find('.compareContainer').prepend($('<img class="hidden"/>').attr('src', diffResult.baseline))
  $el.find('.baselineImgContainer').css('background-image', `url(${diffResult.baseline})`)
  $el.find('.latestImgContainer').css('background-image', `url(${diffResult.latest})`)

  // bind dropdown config options
  $el.find('.ignore-config').val(diffConfig.ignore || 'less')
  $el.find('.resemble-select').change(function() {
    const val = this.value
    if (val === 'nothing') {
      resembleControl.ignoreNothing()
    } else if (val === 'less') {
      resembleControl.ignoreLess()
    } else if (val === 'colors') {
      resembleControl.ignoreColors()
    } else if (val === 'antialiasing') {
      resembleControl.ignoreAntialiasing()
    } else if (val === 'alpha') {
      resembleControl.ignoreAlpha()
    }

    if (['flat', 'movement', 'flatDifferenceIntensity', 'movementDifferenceIntensity', 'diffOnly'].includes(val)) {
      resembleControl.outputSettings({ errorType: val }).repaint()
    }
  })

  // bind button toggle config options
  $el.find('.btn').click((e) => {
    const $this = $(e.currentTarget)

    $this.parent().find('button').removeClass('active')
    $this.addClass('active')

    if ($this.hasClass('pink')) {
      resembleControl
        .outputSettings({
          errorColor: {
            red: 255,
            green: 0,
            blue: 255
          }
        })
        .repaint()
    } else if ($this.hasClass('yellow')) {
      resembleControl
        .outputSettings({
          errorColor: {
            red: 255,
            green: 255,
            blue: 0
          }
        })
        .repaint()
    } else if ($this.hasClass('opaque')) {
      resembleControl.outputSettings({ transparency: 1 }).repaint()
    } else if ($this.hasClass('transparent')) {
      resembleControl.outputSettings({ transparency: 0.3 }).repaint()
    }
  })

  // bind ignored box actions
  $el.on('click', '.ignored-box-ui:not(.active)', (e) => activateIgnoredBox(parseInt($(e.currentTarget).data('box-id'), 10)))
  $el.on('click', '.ignored-box-ui.active', (e) => {
    const $this = $(e.currentTarget)
    // ignore click events that bubble up while we're resizing / dragging an ignore box
    if ($this.hasClass('ui-resizable-resizing') || $this.hasClass('ui-draggable-dragging')) return
    updateIgnoredBox(parseInt($(e.currentTarget).data('box-id'), 10))
  })
  $el.on('contextmenu', '.ignored-box-ui', removeIgnoredBox)
  $el.on('click', '.removeIgnoredBox', removeIgnoredBox)

  // bind 'show config' button click
  $el.find('.showConfig').click(() => {
    const diffOptions = {}

    if (diffResult.engine !== diffResult.defaultEngine) {
      diffOptions.engine = diffResult.engine
    }

    if (diffResult.failureThreshold !== diffResult.defaultFailureThreshold) {
      diffOptions.failureThreshold = diffResult.failureThreshold
    }

    const ignoreOption = $el.find('.ignore-config').val()
    if (ignoreOption !== 'less') diffOptions.ignore = ignoreOption

    if (ignoreOption === diffConfig.ignore && Object.keys(tolerances).length > 0) {
      diffOptions.tolerances = tolerances
    }

    if (diffConfig.ignoreAreasColoredWith) {
      diffOptions.ignoreAreasColoredWith = diffConfig.ignoreAreasColoredWith
    }

    const boxes = ignoredBoxes.map((ignoredBox) => {
      return {
        top: ignoredBox.top,
        left: ignoredBox.left,
        bottom: ignoredBox.bottom,
        right: ignoredBox.right
      }
    })

    if (boxes.length) {
      diffOptions.ignoredBoxes = boxes
    }

    const formatFn = onShowConfig || ((x) => x);

    $el.find('.configModal pre').text(formatFn(JSON.stringify(diffOptions, null, 2), diffConfig))
    $el.find('.configModal .copyConfig').addClass('btn-light').removeClass('btn-success')
    $el.find('.configModal').modal({ keyboard: true })
  })

  // bind 'rebase' button click
  $el.find('.rebase').click(() => {
    if (!onShowRebase) return downloadLatest()

    const txt = onShowRebase(diffConfig, downloadLatest)
    if (!txt) return

    $el.find('.rebaseModal pre').text(txt)
    $el.find('.rebaseModal .copyCmd').addClass('btn-light').removeClass('btn-success')
    $el.find('.rebaseModal').modal({ keyboard: true })
  })

  // bind 'copy' button click for modals
  $el.on('click', '.copy', (e) => {
    const $this = $(e.currentTarget)
    const $tmpTextArea = $('<textarea/>')
    $('body').append($tmpTextArea);
    $tmpTextArea.val($this.closest('.modal').find('pre').text()).select()
    try { document.execCommand('copy') } catch (err) {}
    try { navigator.clipboard.writeText($tmpTextArea.val()) } catch (err) {}
    $tmpTextArea.remove()
    $this.removeClass('btn-light').addClass('btn-success')
  })

  // bind baseline / latest image click events
  const $slider = $el.find('.compareModal .slider')
  $el.find('.baseline img, .latest img').click(() => {
    $slider.css('left', 'calc(50% - 4px)')
    $el.find('.compareModal .modal-body').removeClass('zoomed')
    $el.find('.compareModal').modal({ keyboard: true })
  })
  $el.find('.compareContainer').on('click', (e) => $(e.currentTarget).closest('.modal-body').toggleClass('zoomed'))

  // bind comparison slider to mouse movement
  const $baselineImgContainer = $el.find('.baselineImgContainer')
  $el.find('.compareContainer').on('mousemove', function (e) {
    const $this = $(this)
    const maxWidth = $this.find('img:first')[0].clientWidth - 4
    const offsetX = e.pageX - $this.offset().left

    let sliderX = offsetX <= 4 ? 0 : offsetX - 4
    if (sliderX > maxWidth) sliderX = maxWidth

    $slider.css('left', sliderX)
    $baselineImgContainer.css('right', maxWidth - sliderX)
  })

  // redraw ignore boxes on window resize
  let windowResizeThrottle = { timer: null, isPending: false }
  $(window).resize((e) => {
    if (e.target !== window) return

    if (windowResizeThrottle.timer) {
      windowResizeThrottle.isPending = true
      return
    }

    redrawIgnoreBoxes()

    windowResizeThrottle.timer = setInterval(() => {
      if (!windowResizeThrottle.isPending) {
        clearInterval(windowResizeThrottle.timer)
        windowResizeThrottle.timer = null
        return
      }

      windowResizeThrottle.isPending = false
      redrawIgnoreBoxes()
    }, 50)
  })

  // wait for step contents animation to complete and then execute the diff
  setTimeout(compareImg, 300)


  // -- begin helper function definitions -- \\

  function downloadLatest(filename) {
    const format = diffResult.latest.substring(11, diffResult.latest.indexOf(';'))
    const a = document.createElement('a');
    a.href = diffResult.latest;
    a.download = filename || ('latest.' + format);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  function addIgnoredBox(id, isActive) {
    $el.find('.image-diff').append(`<div data-box-id="${id}" class="ignored-box-ui ignored-box-ui-${id}"></div>`)

    updateIgnoredBox(id, isActive)

    if (isActive) activateIgnoredBox(id)
  }

  function removeIgnoredBox(e) {
    e.preventDefault()

    const boxId = parseInt($(e.currentTarget).data('box-id'), 10)
    $el.find(`.ignored-box-ui-${boxId}`).remove()

    const boxIndex = ignoredBoxes.findIndex((ignoredBox) => ignoredBox.id === boxId)
    ignoredBoxes.splice(boxIndex, 1)

    resembleControl.outputSettings({ ignoredBoxes }).repaint()
  }

  function redrawIgnoreBoxes () {
    $el.find('.ignored-box-ui').each(function () {
      const boxId = parseInt($(this).data('box-id'), 10)
      updateIgnoredBox(boxId, true)
    })
  }

  function updateIgnoredBox(e, suppressRepaint) {
    const boxId = $.isNumeric(e) ? e : parseInt($(e.currentTarget).data('box-id'), 10)
    const ignoredBox = ignoredBoxes.find((ignoredBox) => ignoredBox.id === boxId)
    const { scale, maxWidth, maxHeight } = calcScale()

    ignoredBox.left = greaterOf(0, ignoredBox.left)
    ignoredBox.top = greaterOf(0, ignoredBox.top)
    ignoredBox.right = lessOf(maxWidth, ignoredBox.right)
    ignoredBox.bottom = lessOf(maxHeight, ignoredBox.bottom)

    // force sane values
    if (ignoredBox.left >= ignoredBox.right) {
      ignoredBox.right = lessOf(maxWidth, ignoredBox.left + 5)
      ignoredBox.left = greaterOf(0, ignoredBox.right - 5)
    }

    if (ignoredBox.top >= ignoredBox.bottom) {
      ignoredBox.bottom = lessOf(maxHeight, ignoredBox.top + 5)
      ignoredBox.top = greaterOf(0, ignoredBox.bottom - 5)
    }

    $el.find(`.ignored-box-ui-${boxId}`).css({
      width: (ignoredBox.right - ignoredBox.left) / scale,
      height: (ignoredBox.bottom - ignoredBox.top) / scale,
      top: ignoredBox.top / scale,
      left: ignoredBox.left / scale
    })

    if (!suppressRepaint) {
      resembleControl.outputSettings({ ignoredBoxes }).repaint()
      activateIgnoredBox(null)
    }
  }

  function activateIgnoredBox(id) {
    $el.find('.ignored-box-ui.active').removeClass('active').each(function () {
      $(this).resizable('destroy').draggable('destroy')
    })

    if (id === null) return

    $el.find(`.ignored-box-ui-${id}`)
      .addClass('active')
      .resizable({
        handles: 'all',
        containment: 'parent',
        minHeight: 10,
        minWidth: 10,
        resize(e, ui) {
          const ignoredBox = ignoredBoxes.find((ignoredBox) => ignoredBox.id === id)
          const { scale, maxWidth, maxHeight } = calcScale()

          ignoredBox.left = greaterOf(0, ui.position.left * scale)
          ignoredBox.top = greaterOf(0, ui.position.top * scale)
          ignoredBox.right = lessOf(maxWidth, (ui.position.left + ui.size.width) * scale)
          ignoredBox.bottom = lessOf(maxHeight, (ui.position.top + ui.size.height) * scale)
        }
      })
      .draggable({
        containment: 'parent',
        drag(e, ui) {
          const ignoredBox = ignoredBoxes.find((ignoredBox) => ignoredBox.id === id)
          const { scale, maxWidth, maxHeight } = calcScale()
          const width = (parseInt(ignoredBox.right, 10) - parseInt(ignoredBox.left, 10))
          const height = (parseInt(ignoredBox.bottom, 10) - parseInt(ignoredBox.top, 10))

          if (width === maxWidth) {
            ignoredBox.left = 0
            ignoredBox.right = width
          } else {
            const scaledWidth = width / scale
            ignoredBox.left = greaterOf(0, ui.position.left * scale)
            ignoredBox.right = lessOf(maxWidth, (ui.position.left + scaledWidth) * scale)
          }

          if (height === maxHeight) {
            ignoredBox.top = 0
            ignoredBox.bottom = height
          } else {
            const scaledHeight = height / scale
            ignoredBox.top = greaterOf(0, ui.position.top * scale)
            ignoredBox.bottom = lessOf(maxHeight, (ui.position.top + scaledHeight) * scale)
          }
        }
      })
  }

  function lessOf(a, b) {
    a = parseInt(a, 10)
    b = parseInt(b, 10)
    return a < b ? a : b
  }

  function greaterOf(a, b) {
    a = parseInt(a, 10)
    b = parseInt(b, 10)
    return a > b ? a : b
  }

  function calcScale() {
    const diffImg = $el.find('.img-container img')[0]
    return {
      scale: diffImg.naturalWidth / diffImg.clientWidth,
      maxHeight: diffImg.naturalHeight,
      maxWidth: diffImg.naturalWidth
    }
  }

  function compareImg() {
    const outputSettings = { ignoredBoxes, largeImageThreshold: 0}
    if (diffConfig.ignoreAreasColoredWith) outputSettings.ignoreAreasColoredWith = diffConfig.ignoreAreasColoredWith

    resembleControl = resemble(diffResult.baseline)
        .compareTo(diffResult.latest)
        .outputSettings(outputSettings)

    switch ($el.find('.ignore-config').val()) {
      case 'nothing':
        resembleControl.ignoreNothing()
        break
      case 'antialiasing':
        resembleControl.ignoreAntialiasing()
        break
      case 'colors':
        resembleControl.ignoreColors()
        break
      case 'alpha':
        resembleControl.ignoreAlpha()
        break
      default:
        resembleControl.ignoreLess()
    }

    resembleControl.setupCustomTolerance(tolerances)

    resembleControl = resembleControl.onComplete((data) => {
      const outputImage = new Image()
      if (!firstPaintComplete) {
        firstPaintComplete = true
        outputImage.onload = () => {
          ignoredBoxes.forEach((ignoredBox) => addIgnoredBox(ignoredBox.id, false))
        }
      }

      outputImage.src = data.getImageDataUrl()

      $el.find('.img-container').html(outputImage)

      // open full-size diff image in modal
      $(outputImage).click(() => {
        $el.find('.fullScreenModal .modal-body').html($('<img/>').attr('src', outputImage.src))
        $el.find('.fullScreenModal .modal-body img').click((e) => $(e.currentTarget).closest('.modal-body').toggleClass('zoomed'))
        $el.find('.fullScreenModal .modal-body').removeClass('zoomed')
        $el.find('.fullScreenModal').modal({ keyboard: true })
      })

      // right-click adds new ignore box
      $(outputImage).contextmenu(function (e) {
        e.preventDefault()

        const $this = $(this)
        const { scale } = calcScale()
        const offsetX = e.pageX - $this.offset().left - 50
        const offsetY = e.pageY - $this.offset().top - 50
        const boxId = ignoredBoxes.length

        ignoredBoxes.push({
          id: boxId,
          left: offsetX * scale,
          top: offsetY * scale,
          right: (offsetX + 100) * scale,
          bottom: (offsetY + 100) * scale
        })

        addIgnoredBox(boxId, true)
      })

      $el.find('.mismatch').text(data.misMatchPercentage)
    })
  }

  function createHtml() {
    $el.html(`
    <div class="diff-ui-screenshots">
      <div class="baseline">
        <h3>Baseline</h3>
      </div>
      <div class="diff">
        <h3>Diff</h3>
        <div class="image-diff">
          <div class="img-container"></div>
        </div>
      </div>
      <div class="latest">
        <h3>Latest</h3>
      </div>
    </div>
    <div class="diff-ui-inset">
      <div class="diff-results">
        <div>
          <strong>
            The second image is <span class="mismatch">0.00</span>% different compared to the first.
          </strong>
          <em>
            ${diffResult.ssimMismatchPercentage === undefined ? '' : '(SSIM reported ' + diffResult.ssimMismatchPercentage.toFixed(2) + '% difference)'}
          </em>
        </div>
      </div>
      <div class="diff-ui-controls">
        <div class="form-row">
          <div class="form-group col-sm-4">
            <select class="form-control form-control-sm resemble-select ignore-config">
              <option value="less">Ignore less</option>
              <option value="nothing">Ignore nothing</option>
              <option value="colors">Ignore colors</option>
              <option value="antialiasing">Ignore antialiasing</option>
              <option value="alpha">Ignore alpha</option>
            </select>
          </div>
          <div class="form-group col-sm-4">
            <select class="form-control form-control-sm resemble-select">
              <option value="flat" selected>Flat</option>
              <option value="movement">Movement</option>
              <option value="flatDifferenceIntensity">Flat with diff intensity</option>
              <option value="movementDifferenceIntensity">Movement with diff intensity</option>
              <option value="diffOnly">Diff portion from the input</option>
            </select>
          </div>
          <div class="form-group col-sm-4">
            <button class="btn btn-sm btn-secondary form-control form-control-sm rebase">Rebase</button>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group col-sm-4 btn-group" role="group">
            <button class="btn btn-sm active pink">Pink</button>
            <button class="btn btn-sm light yellow">Yellow</button>
          </div>
          <div class="form-group col-sm-4 btn-group" role="group">
            <button class="btn btn-sm active opaque">Opaque</button>
            <button class="btn btn-sm light transparent">Transparent</button>
          </div>
          <div class="form-group col-sm-4">
            <button class="btn btn-sm btn-info form-control form-control-sm showConfig">Show config</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered fit-content configModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Image Comparison Config</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body"><pre></pre></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
            <button type="button" class="btn btn-light copy">Copy</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered fit-content rebaseModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Rebase</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <pre></pre>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
            <button type="button" class="btn btn-light copy">Copy</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered full-screen fullScreenModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Diff</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body"></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered full-screen compareModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Diff</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <div class="compareContainer">
              <div class="latestImgContainer"></div>
              <div class="baselineImgContainer"></div>
              <div class="slider"><div></div></div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>`)
  }
}
