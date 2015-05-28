var app = {};

// DOCUMENT READY
$(function() {
  // Render calendar from yesterday
  updateCalendarDates(yesterday());

  // Bind weekend collapse
  $("#b_hide_weekend").click(function(){
    // TODO: check if this train reck is the only way to do this
    $("#t_calendar th:nth-of-type(6), #t_calendar td:nth-of-type(6), #t_calendar th:nth-of-type(7), #t_calendar td:nth-of-type(7)").toggle();
  });

  // Bind sidebar collapse
  $("#b_hide_left").click(function() {
    $("#d_left_sidebar").toggleClass("col-hidden col-sm-2");
    updateMainCol();
  });
  
  $("#b_hide_right").click(function() {
    $("#d_right_sidebar").toggleClass("col-hidden col-sm-2");
    updateMainCol();        
  });
  
  // Bind event description show button
  $(".event button").click(function() {
    $(this).next(".e_desc").toggle(500);
  });
  
  // Bind datetime picker
  $(".datetimepicker").datetimepicker();

  // Bind logout button
  $("#btn_logout").click(function() {
    $.ajax("/api/session", {
      method: "DELETE",
      success: function(data) { window.location.reload(); },
      error: function(data) { alert(data.responseJSON.message); }
    });
  });

  // Bind event creation form
  $("#event_form").submit(function(e) {
    e.preventDefault();
    // Seperate datetime into date and time
    var form = $(this);
    var formObj = getFormObj(form);
    var regex = new RegExp('/', "g");
    formObj["startTime"] = formObj["startDate"].split(" ")[1];
    formObj["startDate"] = formObj["startDate"].split(" ")[0].replace(regex, '-');
    formObj["endTime"] = formObj["endDate"].split(" ")[1];
    formObj["endDate"] = formObj["endDate"].split(" ")[0].replace(regex, '-');
    console.log(formObj);
    formObj["timezone"] = jstz.determine().name();
    
    // Submit form
    $.ajax(form.attr("action"), {
      data: JSON.stringify(formObj),
      contentType: "application/json; charset=utf-8",
      dataType: "json",
      method: form.attr("method"),
      success: function(data) {
        toastr.success("Created " + formObj["title"]);
        app.events.push(formObj);
        createEventView(formObj);
      },
      error: function(data) { $("#event_create_errors").text(data.responseJSON.message); }
    });
  });

  // Bind calendar creation form
  $("#calendar_create_form").submit(function(e) {
    e.preventDefault()
    submitAjaxForm($(this), function(data) { toastr.success(data.name + " created!"); refreshCalendar(); }, $("#calendar_create_errors"));
  });

  // Sets up request headers for all subsequent ajax calls
  $.ajaxSetup({
    contentType: "application/json; charset=utf-8",
    dataType: "json",
    beforeSend: function(xhr) {
      xhr.setRequestHeader("Authorization", getCookie("token"));
    }
  });
  
  // Request calendar information
  refreshCalendar();

  // Request user profile information
  refreshUser();
  
  // Bind user profile buttons
  $("#profile_form").hide();
  $("#b_update_profile").click(function() {
    $("#d_user_profile").toggle();
    $("#profile_form").toggle();
  });
  $("#b_cancel_profile").click(function() {
    $("#profile_form").toggle();
    $("#d_user_profile").toggle();
  });
  
  // Bind user profile update form
  // Bind calendar creation form
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
    // TODO: Retrieve more events from server
    
    // get tomorrow's events from local storage
    
    // advance date by 1
    app.current_start_date.setDate(app.current_start_date.getDate() - 1);
    updateCalendarDates(app.current_start_date);
    renderCalendar()
  });

  $("#next_day").click(function() {
    // TODO retrieve more events from server

    // get yesterday's events from local storage

    // shift weekday columns right by one
    app.current_start_date.setDate(app.current_start_date.getDate() + 1);
    updateCalendarDates(app.current_start_date);
    renderCalendar();
  });

  // Retrieve and render calendar events
  $.ajax("/json/calendar.json", {
    success: function(data) {
      app.events = data.events;
      app.joined = [];
      $.each(app.events, function(index, event) {
        createEventView(event);
      });
    },
    error: function(data) {
      console.log("Failed to retrieve calendar events.");
    }
  });
}); // End of document ready

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

// Temporary function to get auth token from cookie
function getCookie(name) {
  var value = "; " + document.cookie;
  var parts = value.split("; " + name + "=");
  if (parts.length == 2) return parts.pop().split(";").shift();
}

// Render a new event on the calendar
function createEventView(model) {
  // expand description on hover
  // find the cell corresponding to start date
  var temp =
  '<div data-event-id="{{eventId}}" class="event">'+
    '<div class="time">'+
      '<dd>{{startDate}}</dd>'+
      '<dd>{{startTime}}</dd>'+
    '</div>'+
    '<div class="header">'+
      '<span class="label label-warning count">{{max}}</span>'+
    '</div>'+
    '<div class="title">{{title}}</div>'+
    '<div class="desc">{{description}}</div>'+
    '<div class="location">'+
      '<span class="glyphicon glyphicon-map-marker"></span> {{location}}'+
    '</div>'+
    '<div class="join">'+
      '<a class="badge" onclick=joinEvent(this)>Join</a>'+
    '</div>'+
  '</div>';
  $("#t_calendar_body").children().each(function(k, elem) {
    if ($(elem).data("date") === model.startDate) {
      var readableDate = formatDate(new Date(model.startDate)).split(" ").reverse().join(" ");
      // append event div
      $(elem).append(temp
          .replace('{{eventId}}', model.eventId)
          .replace('{{startDate}}', readableDate)
          .replace('{{startTime}}', model.startTime)
          .replace('{{max}}', model.max - model.attendees)
          .replace('{{title}}', model.title)
          .replace('{{description}}', model.description)
          .replace('{{location}}', model.location));
    }
    // if event not in view, don't render
  });
}

// Update data-date field of calendar view from startDate
function updateCalendarDates(startDate) {
  // TODO: Handle different time zone
  app.current_start_date = new Date(startDate);
  $("#prev_day").next().text(formatDate(startDate));

  $("#t_calendar_body").children().each(function(k, elem) {
    // update data fields
    var date = startDate.toJSON().split("T")[0];
    $(elem).data("date", date);

    // update heading text
    var heading = $($("#t_calendar_heading").children()[k]);
    heading.text(getWeekDay(startDate));

    // update heading class
    heading.removeClass("th_weekend").removeClass("th_weekday");
    var day = startDate.getDay();
    if (day === 0 || day === 6) {
      heading.addClass("th_weekend");
    } else {
      heading.addClass("th_weekday");
    }

    // clear all current events visible on this day
    $(elem).empty();

    // increment date
    startDate.setDate(startDate.getDate() + 1);
  });

  $("#next_day").prev().text(formatDate(startDate));
}

// Get yesterday as date object
function yesterday() {
  var today = new Date();
  today.setDate(today.getDate() - 1);
  return today;
}

// Update user profile information on view
function refreshUser() {
  $.ajax("/api/user", {
    method: "GET",
    success: function(data) {
        $("[data-bind='email']").text(data.email);
        $("[data-bind='firstName']").text(data.firstName);
        $("[data-bind='lastName']").text(data.lastName);
        $("[data-bind='lastSeen']").text(data.lastSeen);
      }
  });
}

// Update calendars
function refreshCalendar() {
  $.get("/api/subscription/calendar", function(data) {
    if (data.calendarIds.length < 1) {
      return;
    }
    $("#user_calendars").empty();
    $("#select_calendar").empty();
    $.each(data.calendarIds, function(index, calendarId) {
      // For every calendar get calendar data
      var d_json = "{'calendarId':" + calendarId + "}";
      $.get("/api/calendar", { data: d_json }, function(data) {
        // TODO: update global variable with calendar data
        $('#select_calendar')
         .append($("<option></option>")
         .attr("value",calendarId)
         .text(data.name));
        $("<div>").addClass("checkbox").append($("<label>").html('<input type="checkbox" checked>'+data.name)).appendTo("#user_calendars");
      });
    });
  });
}

// Validation of update form
function validateUpdate($form) {
  var form = $form[0];
  var pass_val = form["newPassword"].value;
  
  // Check password length
  if (pass_val.length < 6) {
    $("#profile_errors").text("New password must be at least 6 characters long");
    return true;
  }
  
  // Check confirmation
  var conf_pass_val = form["confPassword"].value;
  if (pass_val !== conf_pass_val) {
    $("#profile_errors").text("Password must match");
    return true;
  }
  return false;
}

// Format date string to May 15
function formatDate(date) {
  var str = date.toDateString();
  return str.substring(str.indexOf(' ') + 1, str.lastIndexOf(' '));
}

// Format day of week, TODO: put this into date prototype
function getWeekDay(date) {
  var str = date.toDateString();
  return str.substring(0, str.indexOf(' '));
}

// Renders all events within the displayed date range
function renderCalendar() {
  $.each(app.events, function(index, event) {
    createEventView(event);
  });
}

// Join an event
function joinEvent(elem) {
  var view = $(elem).closest(".event");
  var eid = view.data("eventId");
  $.ajax("/api/subscription/event", {
    method: "POST",
    data: JSON.stringify({eventId: eid}),
    success: function(data) {
      toastr.error(data.message);
      // add event to joined list
      $.each(app.events, function(k, event) {
        if (event.eventId === eid) {
          app.joined.push(event);
          // update attendees count
          event.attendees++;
          view.children(".count").text(event.max - event.attendees);
          return false;
        }
      });
      // update badge
      $(elem).addClass("progress-bar-success").text("Joined");
    },
    error: function(data) {
      toastr.error("Cannot join event: " + data.responseJSON.message);
    }
  });
}
