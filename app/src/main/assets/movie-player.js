(function () {
    if (window.__tvsitesMovieProbe) return;
    window.__tvsitesMovieProbe = true;
    var prefix = 'TVSITES_MOVIE_MEDIA:';
    var seen = {}, count = 0, ticks = 0;
    var ignored = /(^|\.)(youtube\.com|youtube-nocookie\.com|googlevideo\.com|doubleclick\.net)$/i;
    if (ignored.test(location.hostname)) return;
    function absolute(url) {
        try { var a = document.createElement('a'); a.href = url; return a.href; } catch (e) { return ''; }
    }
    function emit(url, type, video) {
        if (typeof url !== 'string' || !url) return;
        url = absolute(url);
        if (!/^https?:\/\//i.test(url) || /(?:trailer|preroll|doubleclick|\/vast[/?])/i.test(url)) return;
        if (/\.(?:ts|m4s|aac|js|css|jpe?g|png|webp)(?:[?#]|$)/i.test(url)) return;
        if (!video && !/\.(?:m3u8|mpd|mp4)(?:[?#]|$)/i.test(url) &&
            !/(?:mpegurl|dash\+xml|video\/mp4)/i.test(type || '')) return;
        if (seen[url] || count >= 20) return;
        seen[url] = true; count++;
        console.log(prefix + JSON.stringify({url: url, mime: type || '', page: location.href}));
    }
    // These hooks run inside each frame, where cross-origin parents cannot inspect the player.
    if (window.fetch) {
        var fetch = window.fetch;
        window.fetch = function () {
            var result = fetch.apply(this, arguments);
            result.then(function (response) {
                try { emit(response.url, response.headers.get('content-type'), false); } catch (e) {}
            }, function () {});
            return result;
        };
    }
    if (window.XMLHttpRequest) {
        var open = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function () {
            this.addEventListener('load', function () {
                try { emit(this.responseURL, this.getResponseHeader('content-type'), false); } catch (e) {}
            });
            return open.apply(this, arguments);
        };
    }
    function activate() {
        var tracks = document.querySelectorAll('track[src]');
        for (var t = 0; t < tracks.length; t++) {
            if (!/^(bg|bul)$/i.test(tracks[t].srclang) && !/българ|bulgar/i.test(tracks[t].label)) continue;
            var sub = absolute(tracks[t].src);
            if (!seen['sub:'+sub] && /^https?:/i.test(sub)) {seen['sub:'+sub] = true;console.log(prefix+JSON.stringify({url:sub,subtitle:true,page:location.href}));}
        }
        var videos = document.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            emit(v.currentSrc || v.src, v.getAttribute('type'), true);
            var sources = v.querySelectorAll('source[src]');
            for (var j = 0; j < sources.length; j++) emit(sources[j].src, sources[j].type, true);
            v.muted = true;
            try { var playing = v.play(); if (playing && playing.catch) playing.catch(function () {}); } catch (e) {}
        }
        try {
            if (typeof window.jwplayer === 'function') {
                var player = window.jwplayer(), list = player.getPlaylist();
                for (var k = 0; list && k < list.length; k++) {
                    emit(list[k].file, list[k].type, false);
                    var variants = list[k].sources || [];
                    for (var j = 0; j < variants.length; j++) emit(variants[j].file, variants[j].type, false);
                }
                if (player.setMute) player.setMute(true);
                if (player.play) player.play();
            }
        } catch (e) {}
        var buttons = document.querySelectorAll('.vjs-big-play-button,.jw-icon-display,.jw-display-icon-container,.plyr__control--overlaid,[aria-label="Play"],[title="Play"]');
        for (var i = 0; i < buttons.length && i < 3; i++) {
            var r = buttons[i].getBoundingClientRect();
            if (r.width > 4 && r.height > 4) buttons[i].click();
        }
    }
    function tick() {
        try { activate(); } catch (e) {}
        if (++ticks < 30) setTimeout(tick, 650);
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', tick);
    else tick();
})();
