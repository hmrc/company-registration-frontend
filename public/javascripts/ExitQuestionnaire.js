
var submitQuestionnaire = function(ableToAchieve,tryingToDo, satisfaction, meetNeeds, recommendation) {
    if(ableToAchieve !="" && tryingToDo != "" && satisfaction != "" && meetNeeds != "" && recommendation != ""){

        var ableSplit = ableToAchieve.split("_");
        ga('send', 'event', {
            'eventCategory': 'exit_questionnaire',
            'eventAction': 'ableToAchieve',
            'eventLabel': ableSplit[0],
            'eventValue': ableSplit[1]
        });

        var tryingSplit = tryingToDo.split("_");
        ga('send', 'event', {
            'eventCategory': 'exit_questionnaire',
            'eventAction': 'tryingToDo',
            'eventLabel': tryingSplit[0],
            'eventValue': tryingSplit[1]
        });


        var satSplit = satisfaction.split("-");
        ga('send', 'event', {
            'eventCategory': 'exit_questionnaire',
            'eventAction': 'satisfaction',
            'eventLabel': satSplit[0],
            'eventValue': satSplit[1]
        });

        ga('send', 'event', {
            'eventCategory': 'exit_questionnaire',
            'eventAction': 'service',
            'eventLabel': meetNeeds,
            'eventValue': meetNeeds
        });

        var recSplit = recommendation.split("-");
        ga('send', 'event', {
            'eventCategory': 'exit_questionnaire',
            'eventAction': 'recommendation',
            'eventLabel': recSplit[0],
            'eventValue': recSplit[1]
        });

        console.log("all good, GA event sent");
    } else {
        console.log("one of the fields is missing");
    }

        return true;
};

$(document).ready($(function() {
    onloadIfError()
    function onloadIfError() {
        if ($('#ableToAchieve-no_2').is(':checked')) {
            $('#why_not_achieve').show()
        }
    }
    // Show or hide why not box if user selects no or yes
    $('input[name="ableToAchieve"]').click(function(){
        if($('#ableToAchieve-no_2').is(':checked')) {
            $('#why_not_achieve').show()
        }
        else {
            $('#why_not_achieve').hide()
        }
    })


    $('#next').click(function() {

        var satisfaction_rating = ""
        var meet_needs_rating = ""
        var recommendation_rating = ""
        var able_to_achieve = ""
        var trying_to_do = ""

        $('input[name="ableToAchieve"]').each(function() {
            if($(this).is(":checked")){
                able_to_achieve = $(this).attr("value")

            }
        });
        $('input[name="tryingToDo"]').each(function() {
            if($(this).is(":checked")){
                trying_to_do = $(this).attr("value")

            }
        });
        $('input[name="satisfaction"]').each(function() {
             if($(this).is(":checked")){
                 satisfaction_rating = $(this).attr("value")
             }
         });

        $('input[name="meetNeeds"]').each(function() {
            if($(this).is(":checked")){
                meet_needs_rating = $(this).attr("value")

            }
        });

        $('input[name="recommendation"]').each(function() {
            if($(this).is(":checked")){
                recommendation_rating = $(this).attr("value")
            }
        });

        return submitQuestionnaire(able_to_achieve,trying_to_do,satisfaction_rating, meet_needs_rating, recommendation_rating);
    });
}));