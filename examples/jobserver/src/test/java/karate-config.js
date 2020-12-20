function fn() {
    if (karate.env === 'docker') {
        var driverConfig = {
            type: 'chrome',
            showDriverLog: true,
            start: false,
            beforeStart: 'supervisorctl start ffmpeg',
            afterStop: 'supervisorctl stop ffmpeg',
            videoFile: '/tmp/karate.mp4'
        };
        karate.configure('driver', driverConfig);
    } else if (karate.env === 'jobserver') {
        karate.configure('driver', {type: 'chrome', showDriverLog: true, start: false});
    } else {
        karate.configure('driver', {type: 'chrome', showDriverLog: true});
    }
    // karate.configure('report', {showLog: false, showAllSteps: false});
    return {}
}