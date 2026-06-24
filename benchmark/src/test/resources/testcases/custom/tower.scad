// Classical Chess Rook (Tower)
// Designed for support-free 3D printing

// Set high resolution for smooth circular curves
$fn = 100; 

module chess_rook() {
    difference() {
        // --- MAIN SOLID BODY ---
        union() {
            // 1. Base ring (Flat on build plate)
            cylinder(h = 3, r1 = 14, r2 = 14);
            
            // 2. Base taper
            translate([0, 0, 3]) 
                cylinder(h = 5, r1 = 14, r2 = 11);
            
            // 3. Main trunk (Slight inward slope)
            translate([0, 0, 8]) 
                cylinder(h = 22, r1 = 11, r2 = 9);
            
            // 4. Collar (Outward flare)
            translate([0, 0, 30]) 
                cylinder(h = 4, r1 = 9, r2 = 12.5);
            
            // 5. Crown / Head
            translate([0, 0, 34]) 
                cylinder(h = 8, r1 = 12.5, r2 = 13);
        }
        
        // --- SUBTRACTIONS (CUTOUTS) ---
        
        // Hollow out the top bowl
        // Extends slightly above the model to ensure a clean boolean cut
        translate([0, 0, 37.5]) 
            cylinder(h = 6, r = 9.5);
        
        // Crenellations (The tower battlements)
        // Cuts 4 intersecting slots to create 8 distinct pillars
        translate([0, 0, 40]) {
            for(i = [0 : 45 : 135]) {
                rotate([0, 0, i])
                    cube([30, 4.5, 6], center = true);
            }
        }
    }
}

// Render the model
chess_rook();