translate([5, 5, 5]) rotate([0, 0, 45]) difference() {
    cube([20, 20, 20], center = true);
    translate([0, 0, 0]) sphere(r = 11);
}
