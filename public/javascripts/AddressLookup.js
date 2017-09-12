$(document).ready($(function() {

    var ppobAddress = $("#addressChoice-ppob");
    var roAddress = $("#addressChoice-ro");
    var lookup = $("#lookup-address");
    var manual = $("#manual-address");
    var lookupSwitch = $("#switch-lookup");
    var manualSwitch = $("#switch-manual");

    var next = $("#nextButton");

    var errorSummary = $("#error-summary-display")

    var addressFromLookupExists = $("input[name=addressGroup]").length > 0;

    //if lookup error exists - hide non lookup elements
    if(errorSummary.length > 0){
        $.each(errorSummary.find('a'), function(key, value){
            if(value.outerHTML.indexOf("lookup.postcode") > 0) {
                next.hide();
            }
        })
    }

    //if manual entry errors exists - hide non manual entry elements


    //if lookup error exists - hide non lookup elements
    if(errorSummary.length > 0){
        $.each(errorSummary.find('a'), function(key, value){
            if(value.outerHTML.indexOf("ppob.address.houseNameNumber") > 0 ||
               value.outerHTML.indexOf("ppob.address.street") > 0 ||
               value.outerHTML.indexOf("ppob.address.area") > 0 ||
               value.outerHTML.indexOf("ppob.address.postCode") > 0){
               lookup.hide();
               manual.show();
               next.show();
            }
        })
    }

    //enter your address radio button
    ppobAddress.on("click", function () {
        lookup.show();
        manual.hide();
        next.hide();

    });

    //RO Address button
    roAddress.on("click", function () {
        lookup.hide();
        manual.hide();
        next.show();
    });

    //Enter manual address blue button
    manualSwitch.on("click", function () {
        lookup.hide();
        manual.show();
        next.show();
    });

    //on switch-lookup button click
    lookupSwitch.on("click", function () {
        lookup.show();
        manual.hide();
        next.hide();
    });

    //if manual error exists - hide non lookup elements
    if(errorSummary.length > 0){
        $.each(errorSummary.find('a'), function(key, value){
            if($(this).attr("id").indexOf("ppob.address") > -1){
               lookup.hide();
               manual.show();
               next.show();
            }
        })
    }
}))