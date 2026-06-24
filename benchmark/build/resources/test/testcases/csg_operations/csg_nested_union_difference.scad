difference() {
    union() {
        cube([20, 20, 20], center = true);
        translate([10, 0, 0]) sphere(r = 8);
    }
    translate([0, 0, 5]) cylinder(h = 25, r = 4);
}
