var app = {joined:{}};

// DOCUMENT READY
$(function() {
  // Sets up request headers for all subsequent ajax calls
  $.ajaxSetup({
    contentType: "application/json; charset=utf-8",
    dataType: "json",
    beforeSend: function(xhr) {
      xhr.setRequestHeader("Authorization", getCookie("token"));
    }
  });
  
  // Bind refresh button
  $("#b_refresh").click(refreshCalendars);
  
  // Bind weekend collapse
  $("#b_hide_weekend").click(function(){
    // TODO: check if this train reck is the only way to do this
    var sat_index = $('#t_calendar_heading th:contains("Sat")').index()+1;
    var selector = "#t_calendar th:nth-of-type("+sat_index+"), #t_calendar td:nth-of-type("+sat_index+"), #t_calendar th:nth-of-type("+(sat_index+1)+"), #t_calendar td:nth-of-type("+(sat_index+1)+")";
    $(this).parent().toggleClass("active");
    $(selector).toggle();
  });

  // Bind sidebar collapse
  $("#b_hide_left").click(function() {
    var btn = $(this).parent();
    if (btn.hasClass("active")) {
      var width = $("#d_left_sidebar").outerWidth();
      $("#d_left_sidebar").animate({
        left: -width,
        duration: 0.2
      });
      btn.removeClass("active");
    } else {
      $("#d_left_sidebar").animate({
        left: 0,
        duration: 0.2
      });
      btn.addClass("active");
    }

    rebuildCalendar();
  });
  
  // Bind right side bar
  $("#b_hide_right").click(function() {
    var width = $("#d_left_sidebar").outerWidth();
    if ($(this).parent().hasClass("active")) {
      $("#d_right_sidebar").animate({
        right: -width,
        duration: 0.2
      });
      $(this).parent().removeClass("active");
    } else {
      $("#d_right_sidebar").animate({
        right: 0,
        duration: 0.2
      });
      $(this).parent().addClass("active");
    }

    rebuildCalendar();
  });
  
  // mobile actions
  $("body").on("swipeleft", function() {
    //e.stopPropagation();
    // close sidebar
    var width = $("#d_left_sidebar").outerWidth();
    $("#d_left_sidebar").animate({
      left: -width,
      duration: 0.2
    });
    $("#b_hide_left").removeClass("active");
  });

  $("body").on("swiperight", function(e) {
    // open sidebar
    if (e.swipestart.coords[0] > 80) {
      return;
    }
    $("#d_left_sidebar").animate({
      left: 0,
      duration: 0.2
    });
    $("#b_hide_left").addClass("active");
  });

  // Bind logout button
  $("#b_logout").click(function() {
    $.ajax("/api/session", {
      method: "DELETE",
      success: function(data) { window.location.reload(); },
      error: function(data) { alert(data.responseJSON.message); }
    });
  });
  
  // Bind user profile buttons
  $("#b_update_profile").click(function() {
    $("#d_user_profile").toggle();
    $("#profile_form").toggle();
  });
  $("#b_cancel_profile").click(function() {
    $("#profile_form").toggle();
    $("#d_user_profile").toggle();
  });
  
  // Bind user profile update form
  $("#profile_form").submit(function(e) {
    e.preventDefault();
    var form = $(this);
    
    // Validate
    if (validateUpdate(form)) {
      return;
    }
    submitAjaxForm(form, function(data) { toastr.success(data.message); }, $("#profile_errors"));
  });

  // Bind previous and next day button
  $("#prev_day").click(function() {
    // advance date by 1
    app.current_start_date.setDate(app.current_start_date.getDate() - 7);
    refreshEvents();
  });

  $("#next_day").click(function() {
    // shift weekday columns right by one
    app.current_start_date.setDate(app.current_start_date.getDate() + 7);
    refreshEvents();
  });
  
  /*toastr.options = {
    "progressBar": false,
    "positionClass": "toast-bottom-center",
    "onclick": null
  }*/
  
  // Request user profile information
  refreshUser();
  
  // Request calendar information
  refreshCalendars();
  
  // Activate time
  // http://stackoverflow.com/questions/18229022/how-to-show-current-time-in-javascript-in-the-format-hhmmss
  (function () {
    function checkTime(i) {
      return (i < 10) ? "0" + i : i;
    }

    function startTime() {
      var today = new Date(),
      h = checkTime(today.getHours()),
      m = checkTime(today.getMinutes()),
      s = checkTime(today.getSeconds());
      document.getElementById('p_time').innerHTML = h + ":" + m + ":" + s;
      t = setTimeout(function () {
        startTime()
      }, 500);
    }
    startTime();
  })();

  $(window).resize(rebuildCalendar);
}); // End of document ready

// Update user profile information on view
function refreshUser() {
  $.get("/api/user",
    function(data) {
      app.user = data;
      $("[data-bind='email']").text(data.email).val(data.email);
      $("[data-bind='firstName']").text(data.firstName).val(data.firstName);
      $("[data-bind='lastName']").text(data.lastName).val(data.lastName);
      $("[data-bind='lastSeen']").text(data.lastSeen).val(data.lastSeen);
  });
}

// Update main column class whether sizebars are hidden or not
function updateMainCol() {
  var size = 8;
  if ($("#d_right_sidebar").hasClass("col-hidden")) {
    size += 2;
  }
  if ($("#d_left_sidebar").hasClass("col-hidden")) {
    size += 2;
  }
  $("#d_main_col").attr("class", "col-sm-" + size);
}

// Rebuild calendar layout for mobile responsiveness
function rebuildCalendar() {
  // get available space
  var width = (window.innerWidth > 0) ? window.innerWidth : screen.width;
  if (width < 768) {
    // single column fluid layout
    // clear calendar heading and body
    $("#t_calendar_heading").empty().append("<th/>");
    $("#t_calendar_body").empty().append("<td/>");
    app.current_start_date = new Date();
    updateCalendarDates(app.current_start_date);
  } else {
    var left = $("#b_hide_left").parent().hasClass("active") ? $("#d_left_sidebar").outerWidth() : 0;
    var right = $("#b_hide_right").parent().hasClass("active") ? $("#d_right_sidebar").outerWidth() : 0;
    width = width - left - right;
    var days = width / 160 >> 0;
    var current_days = $("#t_calendar_body").children().length;

    if (days !== current_days) {
      var dayOfWeek = app.current_start_date.getDay();

      // clear calendar heading and body
      $("#t_calendar_heading").empty();
      $("#t_calendar_body").empty();

      $(".container").animate({
        "left": left,
        duration: 0.2
      }).css("width", days * 160);

      for (var i = 0; i < days; i++) {
        $("#t_calendar_heading").append("<th/>");
        $("#t_calendar_body").append("<td/>");
      }

      // update navigation button
      updateCalendarDates(app.current_start_date);
    }
  }
}
