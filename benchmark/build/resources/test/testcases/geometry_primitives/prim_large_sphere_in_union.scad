union() {
    translate([0, 0, 0]) sphere(r = 50);
    translate([100, 0, 0]) cube([30, 30, 30]);
    translate([0, 100, 0]) cylinder(h = 40, r = 10);
}
