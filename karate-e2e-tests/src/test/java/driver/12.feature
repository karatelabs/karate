Feature:

Scenario:
* driver 'https://invalid/url'
* def frameTree = driver.send({ method: 'Page.getFrameTree' })
* def unreachableUrl = frameTree.result.frameTree.frame.unreachableUrl
* match unreachableUrl == 'https://invalid/url'
