$(document).ready($(function() {

    var whenRegistered = $("#businessStartDate-whenregistered");
    var futureDate = $("#businessStartDate-futuredate");
    var notPlanningToYet = $("#businessStartDate-notplanningtoyet");
    var toggle = $("#futureDate-hidden");
    var notYetToggle = $("#notPlanningToYet-hidden");

    var director = $("#completionCapacity-director");
    var company_secretary = $("#completionCapacity-company_secretary");
    var agent = $("#completionCapacity-agent");
    var other = $("#completionCapacity-other");
    var otherHidden = $("#other-hidden");

    var no = $("#regularPayments-false");
    var yes = $("#regularPayments-true");
    var noToggle = $("#false-hidden");
    var yesToggle = $("#true-hidden");

    var yesReturnUser = $("#returningUser-true");
    var noReturnUser = $("#returningUser-false");
    var yesToggleReturnUser = $("#true-hidden");

    var CurrentEmail = $("#registrationEmail-currentemail");
    var OtherEmail = $("#registrationEmail-differentemail");
    var noToggleCurrentEmail = $("#differentEmail-hidden");

    var otherGroupName = $("#groupName-othername");
    var otherGroupNameHiddenInput = $("#otherName-hidden");

     if(otherGroupName.is(":checked")){
           otherGroupNameHiddenInput.show();
      } else {
           otherGroupNameHiddenInput.hide();
      }

      var otherGroupNameFields = $('[name="groupName"]')

      otherGroupNameFields.on("change", function () {
          if(otherGroupName.is(":checked")){
                otherGroupNameHiddenInput.show();
           } else {
                otherGroupNameHiddenInput.hide();
           }
           });


    var noutr = $("#groupUtr-noutr");
    var utr = $("#groupUtr-utr");
    var enterUtr = $("#utr-hidden");

    if(utr.is(":checked")){
          enterUtr.show();
     } else {
          enterUtr.hide();
     }

     utr.on("change", function () {
         enterUtr.show();
     });

     noutr.on("change", function () {
         enterUtr.hide();
     });

    if(OtherEmail.is(":checked")){
          noToggleCurrentEmail.show();
     } else {
          noToggleCurrentEmail.hide();
     }

     OtherEmail.on("change", function () {
         noToggleCurrentEmail.show();
     });

     CurrentEmail.on("change", function () {
         noToggleCurrentEmail.hide();
     });

     if(yesReturnUser.is(":checked")){
          yesToggleReturnUser.show();
     } else {
          yesToggleReturnUser.hide();
     }

     yesReturnUser.on("change", function () {
         yesToggleReturnUser.show();
     });

     noReturnUser.on("change", function () {
         yesToggleReturnUser.hide();
     });

     if(no.is(":checked")){
         noToggle.show();
     } else {
         noToggle.hide();
     }

     if(yes.is(":checked")){
          yesToggle.show();
     } else {
          yesToggle.hide();
     }

    no.on("change", function () {
        yesToggle.hide();
        noToggle.show();
    });

    yes.on("change", function () {
        yesToggle.show();
        noToggle.hide();
    });

    if(other.is(":checked")){
        otherHidden.show();
    } else {
        otherHidden.hide();
    }

    director.on("change", function () {
       otherHidden.hide();
    });

    company_secretary.on("change", function () {
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

    $("#submissionFailedReportAProblem").each(function(){
        $(".report-error__toggle").click();
        $(".report-error__toggle").hide();
    });

}));

