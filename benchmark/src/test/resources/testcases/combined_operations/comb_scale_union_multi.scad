scale([1.5, 1.5, 1.5]) union() {
    translate([0, 0, 0]) cube([8, 8, 8]);
    translate([10, 0, 0]) sphere(r = 5);
    translate([0, 10, 0]) cylinder(h = 12, r = 3);
}
