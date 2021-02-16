
$(window).load(function() {

    $("#errorEvent").each(function(){
        var handOffNumber = $(this).attr('class');
        var errorType = $($(this).children('span')[0]).attr('id');
    });
});