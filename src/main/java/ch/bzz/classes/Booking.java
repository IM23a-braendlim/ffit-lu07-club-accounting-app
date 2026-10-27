package ch.bzz.classes;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    private String id;
    private String bookingNumber;
    private String date;
    private String text;
    private Account debitAccount;
    private Account creditAccount;
    private int amount;
    private Project project;
}
