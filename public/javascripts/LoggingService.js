
$(window).load(function() {

    $("#errorEvent").each(function(){
        var handOffNumber = $(this).attr('class');
        var errorType = $($(this).children('span')[0]).attr('id');

        if(errorType === "DecryptionError"){
            ga("send", "event", "DecryptionError", "HandOffError", "Payload " + handOffNumber + " could not be decrypted");
        }
        else if(errorType === "PayloadError") {
            ga("send", "event", "PayloadError", "HandOffError", "Payload " + handOffNumber + " content not as expected");
        }
    });
});