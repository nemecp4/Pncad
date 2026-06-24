base = 20;
h = base / 2;
r = sqrt(base);
translate([0, 0, h]) difference() {
    cube([base, base, h], center = true);
    sphere(r = r);
}
