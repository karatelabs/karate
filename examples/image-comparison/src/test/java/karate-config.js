function fn() {
    karate.configure('retry', { count: 20, interval: 200 })

    return {
        baseUrl: karate.properties['web.url.base'] || 'http://localhost:8080/',

        browsers: [
            {
                deviceType: 'phone',
                width: 375,
                height: 667, 
                userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1'
            },
            {
                deviceType: 'tablet',
                width: 820,
                height: 1180,
                useragent: 'Mozilla/5.0 (iPad; CPU OS 13_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/87.0.4280.77 Mobile/15E148 Safari/604.1'
            }
        ],

        emulateBrowser(deviceType) {
            const browser = karate.get('browsers').find(browser => browser.deviceType === deviceType)
            return driver.emulateDevice(browser.width, browser.height, browser.userAgent)
        }
    }
}