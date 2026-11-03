package com.eyescreening.pterygium;

/** Engineering screening of a numerical fit. This is not a disease/clinical quality classifier. */
final class PhoneMapQuality {
    // Prototype safeguards, not clinically validated thresholds. Radius normalisation makes
    // the residual invariant when all reconstructed dimensions are uniformly scaled.
    static final double MAX_RELATIVE_RMS=.01, MAX_CONDITION=1e9;
    final boolean consistent;
    final double relativeRms;
    final String reason;
    private PhoneMapQuality(boolean consistent,double error,String reason){this.consistent=consistent;relativeRms=error;this.reason=reason;}
    static PhoneMapQuality assess(double rms,double radius,double condition,int meridians){
        double relative=rms/radius;
        if(!Double.isFinite(relative)||rms<0||radius<=0||!Double.isFinite(condition)||condition<=0)
            return new PhoneMapQuality(false,Double.POSITIVE_INFINITY,"The surface calculation is invalid.");
        if(meridians<180)return new PhoneMapQuality(false,relative,"Too little ring coverage for the surface calculation.");
        if(relative>MAX_RELATIVE_RMS)return new PhoneMapQuality(false,relative,"The surface fit disagrees strongly with the tracked rings. This can come from capture position or tracking errors. Try a steadier, centred photo.");
        if(condition>MAX_CONDITION)return new PhoneMapQuality(false,relative,"The available ring points do not constrain the surface reliably. Capture the whole ring reflection more evenly.");
        return new PhoneMapQuality(true,relative,"The surface fit passed the prototype consistency checks. This does not verify calibration, disease status or clinical accuracy.");
    }
    static PhoneMapQuality assess(PhoneMapMath.Maps maps){return assess(maps.fitRms,maps.scale,maps.condition,maps.meridians);}
    static boolean prefer(PhoneMapQuality candidate,PhoneMapQuality previous){
        if(previous==null)return true;
        if(candidate.consistent!=previous.consistent)return candidate.consistent;
        return candidate.relativeRms<previous.relativeRms;
    }
}
