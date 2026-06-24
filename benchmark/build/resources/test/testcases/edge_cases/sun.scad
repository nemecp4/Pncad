// Flat Sun with Flares
// Suitable for FDM 3D printing

sun_radius   = 40;   // radius of center disk
flare_length = 20;   // length of rays
flare_width  = 12;   // width of ray base
flare_count  = 6;   // number of rays
thickness    = 3;    // model thickness

$fn = 120;

module flare() {
    cylinder(d=10, h=10);
}

union() {

    // Sun body
    sphere(r = sun_radius);

    // Flares
    for (i = [0 : flare_count - 1]) {
        rotate(i * 360 / flare_count)
            flare();
    }
}