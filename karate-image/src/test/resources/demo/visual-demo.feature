Feature: visual regression demo

  # A runnable walkthrough of the karate-image API — readable top-to-bottom as a
  # manual demo, and asserted end-to-end by VisualDemoTest (which also writes a real
  # HTML report so you can open it and play with the diff lightbox).
  #
  # The `image` global is activated by karate-boot.js (boot.ext('image')); the runner
  # points baselineDir at a fresh, run-local directory so the establish step always
  # fires. Latest images are passed as classpath: paths — image.compare resolves them
  # through Karate's normal resource prefixes (this:/classpath:/file:).
  #
  # Fixtures: home_v1.png and home_v2.png are the "before" and "after" of one screen
  # (a changed metric, a moved card, and a recoloured/renamed button).

  Scenario: 1 - establish a baseline
    # No baseline exists yet, so the first compare adopts the latest image as the
    # baseline and passes with a note (badge: "baseline established").
    * def r = image.compare('home', 'classpath:demo/screenshots/home_v1.png')
    * match r.baselineEstablished == true
    * match r.pass == true

  Scenario: 2 - an unchanged screen matches the baseline
    * def r = image.compare('home', 'classpath:demo/screenshots/home_v1.png')
    * match r.pass == true
    * match r.mismatchPercentage == 0

  Scenario: 3 - a visual regression is caught
    # The UI changed (home_v2). With failOnMismatch off we inspect the result instead
    # of failing the step — the report still attaches the baseline/latest/diff embed,
    # which the lightbox renders (open the report and try slider / blink / onion-skin).
    * image.failOnMismatch = false
    * def r = image.compare('home', 'classpath:demo/screenshots/home_v2.png')
    * match r.pass == false
    * assert r.mismatchPercentage > 0

  Scenario: 4 - accept the new look via rebase, then it matches
    # The change was intentional — adopt home_v2 as the new baseline, then re-compare.
    * image.rebase('home', 'classpath:demo/screenshots/home_v2.png')
    * def r = image.compare('home', 'classpath:demo/screenshots/home_v2.png')
    * match r.pass == true

  Scenario: 5 - per-name options tune one screen
    # A noisier screen ('home_mobile') gets its own tolerance. image.compare auto-loads
    # <baselineDir>/home_mobile.json by name (no options on the step), and that file
    # raises the threshold — so a difference that would fail the strict suite default
    # (threshold 0) is tolerated here. Precedence: suite config < <name>.json < per-call.
    * def established = image.compare('home_mobile', 'classpath:demo/screenshots/home_v1.png')
    * match established.baselineEstablished == true
    * def r = image.compare('home_mobile', 'classpath:demo/screenshots/home_v2.png')
    * assert r.mismatchPercentage > 0
    * match r.pass == true
