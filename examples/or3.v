module OR(output out1,  input in1, in2,in3);
  wire w1;
  or (w1, in1, in2);
  or (out1, in3, w1);
endmodule
