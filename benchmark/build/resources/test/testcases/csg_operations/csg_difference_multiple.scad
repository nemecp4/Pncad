difference() {
    cube([30, 30, 30], center = true);
    translate([0, 0, 0]) cylinder(h = 30, r = 8);
    translate([10, 10, 0]) cylinder(h = 30, r = 5);
}
