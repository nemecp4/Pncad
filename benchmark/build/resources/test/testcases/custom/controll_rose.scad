//
// Multiport Valve Position Ring
//
// Parametric ring with:
// - 6 valve positions
// - curved engraved labels
// - radial notch markers
//
// Units: mm
//

// =====================================================
// PARAMETERS
// =====================================================

inner_diameter = 80;      // 8 cm
outer_diameter = 120;     // 12 cm

ring_thickness = 6;

// text
font_name = "Arial:style=Bold";
font_size = 5.5;
text_depth = 0.8;

// text placement
text_radius = 50;

// marker placement
marker_radius = 60;


// orientation
start_angle = 0;
tracking = 1.5;    // mm between characters

// labels
labels = [
    "FILTR",
    "RINSE",
    "RECIRCULATE",
    "BACKWASH",
    "CLOSE",
    "WASTE"
];

// =====================================================
// MAIN
// =====================================================

difference()
{
    ring();

    for(i=[0:len(labels)-1])
    {
        angle = start_angle + i * 60;

        #marker_notch(angle);

        curved_text(
            labels[i],
            angle,
            text_radius
        );
    }
}

// =====================================================
// RING
// =====================================================

module ring()
{
    difference()
    {
        cylinder(
            d = outer_diameter,
            h = ring_thickness,
            $fn = 250
        );

        translate([0,0,-0.1])
        cylinder(
            d = inner_diameter,
            h = ring_thickness + 0.2,
            $fn = 250
        );
    }
}

// =====================================================
// CHARACTER WIDTH ESTIMATION
// =====================================================
//
// Relative width coefficients.
// Values are empirical and intended only
// for spacing computation.
//

function char_width(c) =

    (c=="I") ? 0.45 :
    (c=="J") ? 0.55 :
    (c=="L") ? 0.55 :
    (c=="T") ? 0.70 :
    (c=="F") ? 0.70 :
    (c=="R") ? 0.75 :
    (c=="P") ? 0.75 :
    (c=="E") ? 0.75 :
    (c=="C") ? 0.80 :
    (c=="S") ? 0.80 :
    (c=="A") ? 0.85 :
    (c=="V") ? 0.90 :
    (c=="W") ? 1.20 :
    (c=="M") ? 1.20 :
    (c=="O") ? 0.95 :
    (c=="Q") ? 0.95 :
    (c=="U") ? 0.95 :
    (c=="D") ? 0.95 :
    (c=="G") ? 0.95 :
    (c=="N") ? 0.95 :
    (c=="B") ? 0.95 :
    (c=="H") ? 0.95 :
    (c=="K") ? 0.95 :
    (c=="X") ? 0.95 :
    (c=="Y") ? 0.95 :
    (c=="Z") ? 0.95 :
    0.9;

// cumulative width before character index i

function sum_width(txt, idx) =
    (idx < 0)
        ? 0
        : sum_width(txt, idx-1)
          + char_width(txt[idx]);



function text_units(txt) =
    sum_width(txt, len(txt)-1);

function estimated_text_length(txt) =
    text_units(txt) * font_size * 0.75
    +
    max(0, len(txt)-1) * tracking;

function width_before(txt, i) =
    (i <= 0)
        ? 0
        : accumulated_length(txt, i);

function accumulated_length(txt, count) =
    (count <= 0)
        ? 0
        : accumulated_length(txt, count-1)
          + char_width(txt[count-1]) * font_size * 0.75
          + tracking;

// =====================================================
// CURVED TEXT
// =====================================================

module curved_text(txt, center_angle, radius)
{
    text_length =
        estimated_text_length(txt);

    total_angle =
        text_length * 180 /
        (PI * radius);

    start_angle_local =
        center_angle -
        total_angle / 2;

    for(i=[0:len(txt)-1])
    {
        char_offset =
            width_before(txt, i);

        a =
            start_angle_local +
            char_offset *
            180 /
            (PI * radius);

        place_character(
            txt[i],
            a,
            radius
        );
    }
}

// =====================================================
// SINGLE CHARACTER
// =====================================================

module place_character(ch, angle, radius)
{
    rotate([0,0,angle])

    translate([
        radius,
        0,
        ring_thickness - text_depth
    ])

    rotate([0,0,90])

    linear_extrude(
        height = text_depth + 0.05
    )

    text(
        ch,
        size = font_size,
        font = font_name,
        halign = "center",
        valign = "center"
    );
}

// =====================================================
// MARKER NOTCH
// =====================================================
module marker_notch(angle)
{
    notch_depth  = 2.2;   // how deep into the ring
    notch_width  = 3.5;   // opening width at surface
    notch_length = 6;     // along radius direction

    //r = outer_diameter/2 - notch_length/2;

    rotate([0,0,angle])
    translate([marker_radius,0,0])

    difference()
    {
        // material to subtract = V-groove cutter
        translate([0,0,ring_thickness - notch_depth])
        linear_extrude(height = notch_depth + 0.2)

        polygon(points=[
            [-notch_length/2, -notch_width/2],
            [0, 0],
            [-notch_length/2,  notch_width/2]
        ]);
    }
}

          