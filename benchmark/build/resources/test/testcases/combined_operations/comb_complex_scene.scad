union() {
    color("red") translate([0, 0, 0]) cube([10, 10, 10]);
    color("blue") translate([15, 0, 0]) sphere(r = 6);
    color("green") translate([0, 15, 0]) cylinder(h = 10, r = 4);
}
