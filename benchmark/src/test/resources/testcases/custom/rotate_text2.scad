text_size = 20;
text_height = 5;

linear_extrude(height = text_height)
    text(
        text = "123456789",
        size = text_size,
        font = "Liberation Sans",
        halign = "center",
        valign = "center"
    );

translate([100,0,0]) rotate([0,0,90])
linear_extrude(height = text_height)
    text(
        text = "123456789",
        size = text_size,
        font = "Liberation Sans",
        halign = "center",
        valign = "center"
    );

