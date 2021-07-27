Feature:

Scenario:
* driver 'https://invalid/url'
* def frameTree = driver.send(karate.toMap({ method: 'Page.getFrameTree' }))
* def unreachableUrl = frameTree.result.frameTree.frame.unreachableUrl
* match unreachableUrl == 'https://invalid/url'
