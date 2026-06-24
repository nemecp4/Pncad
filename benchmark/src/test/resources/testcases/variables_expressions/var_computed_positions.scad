offset = 15;
rad = 5;
union() {
    translate([0, 0, 0]) sphere(r = rad);
    translate([offset, 0, 0]) sphere(r = rad);
    translate([offset / 2, offset, 0]) sphere(r = rad);
}
