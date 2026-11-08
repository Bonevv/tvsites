(function () {
    if (window.__tvsitesOriginalPlayer) return;
    window.__tvsitesOriginalPlayer = true;
    if (/(^|\.)(youtube\.com|youtube-nocookie\.com|doubleclick\.net)$/i.test(location.hostname)) return;
    var prefix = 'TVSITES_ORIGINAL:', frame = 'f' + Math.random().toString(36).slice(2), root = window === window.top;
    var ready = false, episodeReady = true, jw = null, bound = false, attempts = 0, pendingSeek = null, lastPosition = 0, pendingControl = null;
    function send(value) { console.log(prefix + JSON.stringify(value)); }
    function publish() {
        var tracks = [], backend = 'html', current = -1;
        try {
            if (typeof window.jwplayer === 'function') {
                jw = window.jwplayer();
                var captions = jw.getCaptionsList();
                current = jw.getCurrentCaptions(); backend = 'jw';
                for (var i = 0; captions && i < captions.length; i++) {
                    var c = captions[i];
                    if (/^(off|none|disabled|изключени)$/i.test(c.label || c.id || '')) continue;
                    tracks.push({index:i,label:c.label || c.language || ('Субтитри '+i),language:c.language || '',selected:current===i});
                }
            }
        } catch (e) { jw = null; }
        if (!jw) {
            var video = document.querySelector('video');
            if (video && video.textTracks) for (var i=0;i<video.textTracks.length;i++) {
                var t=video.textTracks[i];
                if(t.kind!=='subtitles' && t.kind!=='captions')continue;
                tracks.push({index:i,label:t.label || t.language || ('Субтитри '+(i+1)),language:t.language || '',selected:t.mode==='showing'});
            }
        }
        if(tracks.length)send({type:'captions',frame:frame,backend:backend,tracks:tracks.slice(0,24)});
    }
    function seek() {
        if(pendingSeek===null)return;
        try{if(typeof window.jwplayer==='function'){var p=window.jwplayer();if(p.getDuration()>0){p.seek(pendingSeek);pendingSeek=null;return;}}}catch(e){}
        var v=document.querySelector('video');if(v && v.readyState>0){try{v.currentTime=pendingSeek;pendingSeek=null;}catch(e){}}
    }
    function media() {
        try{if(typeof window.jwplayer==='function'){var p=window.jwplayer();if(p.getDuration()>0)return {player:p,position:p.getPosition(),duration:p.getDuration(),jw:true};}}catch(e){}
        var v=document.querySelector('video');if(v && isFinite(v.duration) && v.duration>0)return {player:v,position:v.currentTime,duration:v.duration,jw:false};
        return null;
    }
    function confirmControl() {
        var c=pendingControl;if(!c)return;
        var m=media();if(!m)return;
        if(c.action==='seekBy' && Math.abs(m.position-c.target)<1.5){
            pendingControl=null;send({type:Math.abs(m.position-c.from)>0.1?'seeked':'controlled',request:c.request,position:m.position,frame:frame});
        }else if(c.action==='toggle'){
            var paused=m.jw?/^(paused|idle|complete)$/.test(m.player.getState()):m.player.paused;
            if(paused===c.paused){pendingControl=null;send({type:'controlled',request:c.request,frame:frame});}
        }
    }
    function position(force) {
        confirmControl();var now=Date.now();if(!force && now-lastPosition<5000)return;lastPosition=now;
        var m=media();if(m)send({type:'position',frame:frame,position:m.position,duration:m.duration});
    }
    function remote(command) {
        var m=media();if(!m)return;
        pendingControl={action:command.action,request:command.request,from:m.position};var c=pendingControl;
        try{
            if(command.action==='seekBy'){
                if(!m.jw && (!m.player.seekable || !m.player.seekable.length)){pendingControl=null;send({type:'unavailable',request:command.request});return;}
                c.target=Math.max(0,Math.min(Math.max(0,m.duration-0.25),m.position+command.seconds));
                if(m.jw)m.player.seek(c.target);else m.player.currentTime=c.target;
            }else{
                var paused=m.jw?/^(paused|idle|complete)$/.test(m.player.getState()):m.player.paused;c.paused=!paused;
                if(m.jw){if(paused)m.player.play(true);else m.player.pause(true);}else if(paused){var result=m.player.play();if(result && result.catch)result.catch(function(){});}else m.player.pause();
            }
            var tries=0;function check(){if(pendingControl!==c)return;try{confirmControl();}catch(e){}if(pendingControl===c && ++tries<16)setTimeout(check,100);}setTimeout(check,100);
        }catch(e){pendingControl=null;send({type:'unavailable',request:command.request});}
    }
    function control(command) {
        if(command.frame && command.frame!==frame)return;
        try {
            if(command.action==='seekBy' || command.action==='toggle'){remote(command);return;}
            if(command.action==='list'){publish();return;}
            if(command.action==='seek'){pendingSeek=command.position;seek();return;}
            if(command.action==='off' || command.backend==='jw') {
                if(typeof window.jwplayer==='function') {
                    var player=window.jwplayer();
                    if(player.setCurrentCaptions)player.setCurrentCaptions(command.action==='off'?0:command.index);
                }
            }
            var video=document.querySelector('video');
            if(video && video.textTracks && (command.action==='off' || command.backend==='html')) {
                for(var i=0;i<video.textTracks.length;i++) {
                    var t=video.textTracks[i];if(t.kind==='subtitles' || t.kind==='captions')t.mode=command.action!=='off' && i===command.index?'showing':'disabled';
                }
            }
            if(command.action==='open') {
                var button=document.querySelector('.jw-icon-cc,.vjs-subs-caps-button,.plyr__controls [data-plyr="captions"],button[aria-label*="subtitles"],button[aria-label*="Subtitles"],button[aria-label*="Captions"],button[aria-label*="Субтитри"]');
                if(button)button.click();
            }
            publish();
        }catch(e){}
    }
    window.__tvsitesOriginalCommand=function(command) {
        control(command);
        for(var i=0;i<window.frames.length;i++)try{window.frames[i].postMessage({tvsitesOriginal:true,command:command},'*');}catch(e){}
    };
    window.addEventListener('message',function(event){var data=event.data;if(data && data.tvsitesOriginal===true && data.command)window.__tvsitesOriginalCommand(data.command);});
    function fit() {
        if(window.__tvsitesControlsOnly || !root || !episodeReady)return;
        var node=document.querySelector('.pmovie__player,.movieplay,[data-player-panel],#video-stage,#player-box,#player,.player-container,.player-wrapper,.react-player,.jwplayer,.video-js,.plyr');
        if(node && !node.querySelector('video,iframe,canvas') && node.tagName!=='VIDEO' && node.tagName!=='IFRAME')return;
        if(!node){
            var candidates=document.querySelectorAll('video,iframe');
            for(var i=0;i<candidates.length;i++){
                var e=candidates[i];if(e.src && /youtube|doubleclick|googlesyndication/i.test(e.src))continue;
                var bounds=e.getBoundingClientRect();if(bounds.width<Math.min(400,innerWidth*0.5) || bounds.height<120)continue;
                node=e;break;
            }
        }
        if(!node)return;
        var parent=node;
        while(parent && parent!==document.body){
            var container=parent.parentElement;if(!container)break;
            for(var j=0;j<container.children.length;j++){var sibling=container.children[j];if(sibling!==parent && sibling.tagName!=='SCRIPT' && sibling.tagName!=='STYLE')sibling.style.setProperty('display','none','important');}
            container.style.setProperty('margin','0','important');container.style.setProperty('padding','0','important');parent=container;
        }
        document.documentElement.style.overflow='hidden';document.body.style.overflow='hidden';document.body.style.background='#000';
        node.style.cssText+=';position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;max-width:none!important;margin:0!important;padding:0!important;z-index:2147483000!important;background:#000!important;';
        var controls=node.querySelector('.pmovie__player-controls');
        var stages=node.querySelectorAll('.video-responsive,.tabs-block__content,.video-stage,.player-frame');
        for(var i=0;i<stages.length;i++)stages[i].style.cssText+=';height:'+ (controls?'calc(100vh - 50px)':'100%')+'!important;max-height:none!important;';
        var media=node.querySelectorAll('iframe,video');for(var i=0;i<media.length;i++){media[i].style.width='100%';media[i].style.height='100%';}
        if(!ready){ready=true;send({type:'ready'});}
    }
    function activate() {
        if(window.__tvsitesControlsOnly || !root || ready)return;
        var wanted=location.hash.match(/tvsites-episode=([0-9]+)/);
        if(wanted){
            episodeReady=false;
            var episodeButtons=document.querySelectorAll('button,[data-tv_link]');
            for(var e=0;e<episodeButtons.length;e++)if(episodeButtons[e].textContent.trim()==='Епизод '+wanted[1] || episodeButtons[e].getAttribute('data-tv_link')===wanted[1]){
                var button=episodeButtons[e];
                episodeReady=button.getAttribute('aria-selected')==='true' || button.getAttribute('data-state')==='active' || button.classList.contains('active') || button.classList.contains('is-active') || button.classList.contains('bg-red-600') || button.classList.contains('bg-primary') || button.classList.contains('is-active');
                if(!episodeReady)button.click();break;
            }
        }
        var frames=document.querySelector('.pmovie__player iframe,.movieplay iframe,#player-box iframe,#video-stage iframe');
        if(!frames){
            var tab=document.querySelector('.tab-btn[data-player]:not([data-player="trailer"]):not([data-player="help"]),.tabs-block__select [data-link]');
            if(tab && !window.__tvsitesOriginalTab){var ev=document.createEvent('Event');ev.initEvent('pointerup',true,true);tab.dispatchEvent(ev);tab.click();window.__tvsitesOriginalTab=true;}
            var overlay=document.querySelector('.container_play .play,.icon_play');if(overlay)overlay.click();
            var buttons=document.querySelectorAll('button');for(var i=0;i<buttons.length;i++)if(/^Гледай( сега| онлайн)?$/i.test(buttons[i].textContent.trim())){buttons[i].click();break;}
        }
    }
    function playing(){seek();position(true);var m=media();send({type:'playing',frame:frame,duration:m?m.duration:0});confirmControl();}
    function ended(){position(true);var m=media();if(m)send({type:'ended',frame:frame,duration:m.duration,position:m.position});}
    function bind() {
        if(bound)return;
        try{if(typeof window.jwplayer==='function'){var p=window.jwplayer();p.on('captionsList',publish);p.on('captionsChanged',publish);p.on('play',playing);p.on('pause',confirmControl);p.on('ready',seek);p.on('time',function(){position(false);});p.on('seeked',function(){position(true);});p.on('complete',ended);bound=true;publish();if(p.getState && p.getState()==='playing')playing();return;}}catch(e){}
        var video=document.querySelector('video');if(video){video.addEventListener('play',playing);video.addEventListener('pause',confirmControl);video.addEventListener('ended',ended);video.addEventListener('timeupdate',function(){position(false);});video.addEventListener('seeked',function(){position(true);});video.addEventListener('loadedmetadata',function(){seek();publish();});if(video.textTracks && video.textTracks.addEventListener)video.textTracks.addEventListener('addtrack',publish);bound=true;publish();if(!video.paused)playing();}
    }
    function tick(){
        try{activate();fit();bind();}catch(e){}
        if(++attempts<30 && (window.__tvsitesControlsOnly?!bound:root?(!ready || !bound):!bound))setTimeout(tick,700);
    }
    var started=false;function start(){if(started)return;started=true;tick();}
    if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',start);setTimeout(start,1000);}else start();
})();
