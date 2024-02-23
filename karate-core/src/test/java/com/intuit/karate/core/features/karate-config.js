function karateConfig() {
  const config = {}

  const RemainingFeaturesTest = Java.type('com.intuit.karate.core.features.RemainingFeaturesTest')
  config.remainingFeatures = RemainingFeaturesTest.remainingFeatures

  return config
}