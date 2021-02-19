
$(document).ready($(function() {
    $('#next').click(function() {
        var selection = "";

        $('input[name="businessStartDate"]').each(function() {
            if($(this).is(":checked")){
                selection = $(this).attr("value")
            }
        });
    });
}));