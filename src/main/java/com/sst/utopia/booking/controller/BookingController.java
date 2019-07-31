package com.sst.utopia.booking.controller;

import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sst.utopia.booking.model.PaymentAmount;
import com.sst.utopia.booking.model.SeatLocation;
import com.sst.utopia.booking.model.Ticket;
import com.sst.utopia.booking.model.User;
import com.sst.utopia.booking.service.BookingService;

/**
 * Controller to present the booking service to the microservices that provide
 * the client-facing API.
 *
 * @author Jonathan Lovelace
 */
@RestController
@RequestMapping("/booking")
public class BookingController {
	/**
	 * Service class used to handle requests.
	 */
	@Autowired
	private BookingService service;
	/**
	 * Reserve a ticket for the given seat.
	 * FIXME: Allow getting the user from headers (injected by the security layer)
	 * @param flight the flight number of the flight
	 * @param row the row number of the seat
	 * @param seat the seat within the row
	 * @param user the user details
	 */
	@PostMapping("/book/flights/{flight}/rows/{row}/seats/{seat}")
	public ResponseEntity<Ticket> bookTicket(@PathVariable final int flight,
			@PathVariable final int row, @PathVariable final String seat,
			@RequestBody final User user) {
		try {
			return new ResponseEntity<>(service.bookTicket(
					new SeatLocation(service.getFlight(flight), row, seat), user),
					HttpStatus.CREATED);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		} catch (final DataIntegrityViolationException|InvalidDataAccessApiUsageException except) {
			// FIXME: This might well also catch exceptions when flight/row/seat isn't in DB
			// TODO: Should it be UNAUTHORIZED instead?
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	/**
	 * Accept payment for a given reserved seat.
	 * @param flight the flight number of the flight
	 * @param row the row number of the seat
	 * @param seat the seat within the row
	 * @param payment the price the customer has paid for the ticket
	 */
	@PutMapping("/pay/flights/{flight}/rows/{row}/seats/{seat}")
	public ResponseEntity<Ticket> acceptPayment(@PathVariable final int flight,
			@PathVariable final int row, @PathVariable final String seat,
			@RequestBody final PaymentAmount payment) {
		try {
			service.acceptPayment(
					service.getTicket(
							new SeatLocation(service.getFlight(flight), row, seat)),
					payment.getPrice());
			return new ResponseEntity<>(HttpStatus.OK);
		} catch (final IllegalStateException except) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.GONE);
		} catch (final NoSuchElementException except) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Accept payment for a given reserved seat.
	 * @param bookingId the ID code of the booking
	 * @param payment the price the customer has paid for the ticket
	 */
	@PutMapping("/pay/bookings/{bookingId}")
	public ResponseEntity<Ticket> acceptPaymentForBookingId(
			@PathVariable final String bookingId,
			@RequestBody final PaymentAmount payment) {
		try {
			return new ResponseEntity<>(
					service.acceptPayment(bookingId, payment.getPrice()),
					HttpStatus.OK);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.GONE);
		} catch (final IllegalStateException except) {
			if (except.getMessage().contains("Uniqueness")) {
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			} else {
				return new ResponseEntity<>(HttpStatus.CONFLICT);
			}
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	/**
	 * Cancel unpaid reservation for a given seat. TODO: Only the ticket-holder
	 * should be able to cancel it
	 *
	 * @param flight the flight number of the flight
	 * @param row    the row number of the seat
	 * @param seat   the seat within the row
	 */
	@DeleteMapping("/book/flights/{flight}/rows/{row}/seats/{seat}")
	public ResponseEntity<Object> cancelReservation(@PathVariable final int flight,
			@PathVariable final int row, @PathVariable final String seat) {
		try {
			service.cancelPendingReservation(service.getTicket(
					new SeatLocation(service.getFlight(flight), row, seat)));
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} catch (final NoSuchElementException except) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Cancel unpaid reservation for a given booking-ID. TODO: only the ticket
	 * holder should be able to cancel it
	 *
	 * @param bookingId the booking-ID for the seat
	 */
	@DeleteMapping("/book/bookings/{bookingId}")
	public ResponseEntity<Object> cancelBookingById(
			@PathVariable final String bookingId) {
		try {
			service.cancelPendingReservation(bookingId);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Extend the reservation timeout for the given unpaid booking. TODO: limit the
	 * number of times this is allowed
	 *
	 * @param flight the flight number of the flight
	 * @param row    the row number of the seat
	 * @param seat   the seat within the row
	 */
	@PutMapping("/extend/flights/{flight}/rows/{row}/seats/{seat}")
	public ResponseEntity<Object> extendTimeout(@PathVariable final int flight,
			@PathVariable final int row, @PathVariable final String seat) {
		try {
			service.extendReservationTimeout(service.getTicket(
					new SeatLocation(service.getFlight(flight), row, seat)));
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} catch (final NoSuchElementException except) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.GONE);
		} catch (final IllegalStateException except) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Extend the reservation timeout for the given unpaid booking. TODO: limit the
	 * number of times this is allowed
	 *
	 * @param bookingId the booking-ID for the seat
	 */
	@PutMapping("/extend/bookings/{bookingId}")
	public ResponseEntity<Object> extendTimeout(@PathVariable final String bookingId) {
		try {
			service.extendReservationTimeout(bookingId);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} catch (final IllegalArgumentException except) {
			return new ResponseEntity<>(HttpStatus.GONE);
		} catch (final IllegalStateException except) {
			if (except.getMessage().contains("Uniqueness")) {
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			} else {
				return new ResponseEntity<>(HttpStatus.CONFLICT);
			}
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Get the details of a ticket.
	 *
	 * @param flightId the flight number of the flight
	 * @param row    the row number of the seat
	 * @param seatId   the seat within the row
	 */
	@GetMapping("/details/flights/{flightId}/rows/{row}/seats/{seatId}")
	public ResponseEntity<Ticket> getBookingDetails(@PathVariable final int flightId,
			@PathVariable final int row, @PathVariable final String seatId) {
		try {
			return new ResponseEntity<>(service.getTicket(
					new SeatLocation(service.getFlight(flightId), row, seatId)),
					HttpStatus.OK);
		} catch (final NoSuchElementException except) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		} catch (final Exception except) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Get the details of a ticket by its booking ID.
	 * @param bookingId the booking ID for the ticket.
	 */
	@GetMapping("/details/bookings/{bookingId}")
	public ResponseEntity<Ticket> getBookingDetailsById(
			@PathVariable final String bookingId) {
		try {
			final Ticket ticket = service.getBooking(bookingId);
			if (ticket == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			} else {
				return new ResponseEntity<>(ticket, HttpStatus.OK);
			}
		} catch (final Exception exception) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
