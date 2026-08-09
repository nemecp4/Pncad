text_size = 20;
text_height = 5;


translate([0,0,0])
linear_extrude(height = text_height)
    text(
        text = "ABCDEFGHIJKLMNOPRSTQUVWZ",
        size = text_size,
        font = "Liberation Sans",
        halign = "center",
        valign = "center"
    );

translate([0,30,0]) rotate([0,0,45])
linear_extrude(height = text_height)
    text(
        text = "ABCDEFGHIJKLMNOPRSTQUVWZ",
        size = text_size,
        font = "Liberation Sans",
        halign = "center",
        valign = "center"
    );
