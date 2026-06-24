union() {
    difference() {
        intersection() {
            cube([20, 20, 20], center = true);
            sphere(r = 14);
        }
        cylinder(h = 25, r = 5);
    }
    translate([15, 0, 0]) sphere(r = 4);
}
