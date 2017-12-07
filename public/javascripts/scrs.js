$(document).ready($(function() {

    var whenRegistered = $("#businessStartDate-whenregistered");
    var futureDate = $("#businessStartDate-futuredate");
    var notPlanningToYet = $("#businessStartDate-notplanningtoyet");
    var toggle = $("#futureDate-hidden");
    var notYetToggle = $("#notPlanningToYet-hidden");

    var director = $("#completionCapacity-director");
    var agent = $("#completionCapacity-agent");
    var other = $("#completionCapacity-other");
    var otherHidden = $("#other-hidden");

    if(other.is(":checked")){
        otherHidden.show();
    } else {
        otherHidden.hide();
    }

    director.on("change", function () {
       otherHidden.hide();
    });

    agent.on("change", function () {
       otherHidden.hide()
    });

    other.on("change", function () {
      otherHidden.show();
    });

    if(futureDate.is(":checked")){
        toggle.show();
    } else {
        toggle.hide();
    }

    if(notPlanningToYet.is(":checked")){
        notYetToggle.show();
    } else {
        notYetToggle.hide();
    }

    whenRegistered.on("change", function () {
        toggle.hide();
        notYetToggle.hide();
    });

    notPlanningToYet.on("change", function () {
        toggle.hide();
        notYetToggle.show();
    });

    futureDate.on("change", function () {
        toggle.show();
        notYetToggle.hide();
    });

    //////////////////////////////////////////////
     var hMRCEndDate = $("#choice-hmrc_defined");
     var companyEndDate = $("#choice-company_defined");
     var companyDateHidden =$("#COMPANY_DEFINED-hidden")

    hMRCEndDate.on("change", function () {
    companyDateHidden.hide();
    });

    companyEndDate.on("change", function () {
    companyDateHidden.show();
    });

    if(companyEndDate.is(":checked")){
            companyDateHidden.show();
        } else {
            companyDateHidden.hide();
        }

    $('[data-metrics]').each(function() {
        var metrics = $(this).attr('data-metrics');
        var parts = metrics.split(':');
        ga('send', 'event', parts[0], parts[1], parts[2]);
    });

    $('[link-analytics]').click(function() {
        var metrics = $(this).attr('link-analytics');
        var parts = metrics.split(':');
//                                            Page      Link
        ga('send', 'event', 'LinkUsed', parts[0], parts[1]);
    });

}));