Feature: visual regression demo

  # A runnable walkthrough of the karate-image API — readable top-to-bottom as a
  # manual demo, and asserted end-to-end by VisualDemoTest (which also writes a real
  # HTML report so you can open it and play with the diff lightbox).
  #
  # The `image` global is activated by karate-boot.js (boot.ext('image')); the runner
  # points baselineDir at a fresh, run-local directory so the establish step always
  # fires. Latest images are passed as classpath: paths — image.diff resolves them
  # through Karate's normal resource prefixes (this:/classpath:/file:).

  Background:
    # Fixtures: home_v1.png and home_v2.png are the "before" and "after" of one screen
    # (a changed metric, a moved card, and a recoloured/renamed button).
    * def v1 = 'classpath:demo/screenshots/home_v1.png'
    * def v2 = 'classpath:demo/screenshots/home_v2.png'

    # The screenGrab-style recipe (see the README). Orchestration lives here, in JS, not
    # in the engine: resolve the baseline by name, auto-establish it the first time, run
    # the pure image.diff, emit the multi-part embed, and fail the step on a real mismatch.
    # Copy/tweak this in your own project; real usage passes screenshot() as `latest`.
    * def grab =
      """
      function(name, latest) {
        const p = image.resolve(name)
        const established = !p.baselineExists
        if (established) image.write(name, latest)      // adopt latest as the baseline
        const r = image.diff(name, latest)              // pure: compute + build embed
        r.baselineEstablished = established
        if (r.embed) {
          r.embed.meta.baselineEstablished = established
          karate.embed(r.embed)                         // attach baseline/latest/diff to the report
        }
        if (!r.pass && image.failOnMismatch !== false) karate.fail(r.error.message)
        return r
      }
      """

  Scenario: 1 - establish a baseline
    # No baseline exists yet, so the first grab adopts the latest image as the
    # baseline and passes with a note (badge: "baseline established").
    * def r = grab('home', v1)
    * match r.baselineEstablished == true
    * match r.pass == true

  Scenario: 2 - an unchanged screen matches the baseline
    * def r = grab('home', v1)
    * match r.pass == true
    * match r.mismatchPercentage == 0

  Scenario: 3 - a visual regression is caught
    # The UI changed (home_v2). With failOnMismatch off we inspect the result instead
    # of failing the step — the report still attaches the baseline/latest/diff embed,
    # which the lightbox renders (open the report and try slider / blink / onion-skin).
    * image.failOnMismatch = false
    * def r = grab('home', v2)
    * match r.pass == false
    * match r.mismatch == true
    * assert r.mismatchPercentage > 0

  Scenario: 4 - accept the new look via rebase, then it matches
    # The change was intentional — adopt home_v2 as the new baseline, then re-compare.
    * image.write('home', v2)
    * def r = grab('home', v2)
    * match r.pass == true

  Scenario: 5 - per-name options tune one screen
    # A noisier screen ('home_mobile') gets its own tolerance. image.diff auto-loads
    # <optionsDir>/home_mobile.json by name (no options on the step), and that file
    # raises the threshold — so a difference that would fail the strict suite default
    # (threshold 0) is tolerated here. Precedence: suite config < <name>.json < per-call.
    * def established = grab('home_mobile', v1)
    * match established.baselineEstablished == true
    * def r = grab('home_mobile', v2)
    * assert r.mismatchPercentage > 0
    * match r.pass == true
