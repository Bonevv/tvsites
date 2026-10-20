package com.example.tvsites;

/** Published station streams. Portals remain available separately for manual listening. */
final class RadioStation {
    final String id, name, stream, website;
    RadioStation(String id, String name, String stream, String website) {
        this.id = id; this.name = name; this.stream = stream; this.website = website;
    }
    static final RadioStation[] DEFAULTS = {
        new RadioStation("bg", "БГ Радио", "https://play.global.audio/bgradio128", "https://www.bgradio.bg/"),
        new RadioStation("city", "Радио City", "https://play.global.audio/city.opus", "https://www.radiocity.bg/"),
        new RadioStation("nova", "Радио NOVA", "https://play.global.audio/nova.opus", "https://www.radionova.bg/"),
        new RadioStation("veronika", "Радио Вероника", "https://play.global.audio/veronika64", "https://www.radioveronika.bg/"),
        new RadioStation("novanews", "Радио NOVA NEWS", "https://play.global.audio/novanews.aac", "https://www.radionovanews.bg/"),
        new RadioStation("radio1", "Радио 1", "http://stream.metacast.eu/radio1128", "https://www.radio1.bg/"),
        new RadioStation("radio1rock", "Радио 1 Rock", "http://play.global.audio/radio1rockhi.aac", "https://www.radio1rock.bg/"),
        new RadioStation("sofia", "БНР Радио София", "http://stream.bnr.bg:8030/radio-sofia.aac", "https://bnr.bg/sofia/")
    };
}
