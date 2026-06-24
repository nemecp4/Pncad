// Flat Sun with Flares
// Suitable for FDM 3D printing

sun_radius   = 40;   // radius of center disk
flare_length = 20;   // length of rays
flare_width  = 12;   // width of ray base
flare_count  = 16;   // number of rays
thickness    = 3;    // model thickness

$fn = 120;

module flare() {
    polygon([
        [sun_radius, -flare_width/2],
        [sun_radius + flare_length, 0],
        [sun_radius, flare_width/2]
    ]);
}

linear_extrude(height = thickness)
union() {

    // Sun body
    circle(r = sun_radius);

    // Flares
    for (i = [0 : flare_count - 1]) {
        rotate(i * 360 / flare_count)
            flare();
    }
}