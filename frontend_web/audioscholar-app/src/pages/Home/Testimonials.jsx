const Testimonials = () => {
  const testimonials = [
    {
      quote: "AudioScholar has completely transformed the user's voice in practice. The user receives some incredibly convenient and trusted examples of shared access.",
      author: "Illustration",
      link: "https://www.youtube.com/watch?v=JXQW",
      role: "Content Creator"
    },
    {
      quote: "This background recording volume is an open-source format. I can have a variety of online formats such as streaming, mobile storage, etc.",
      author: "Michael Clark",
      link: "https://www.microsoft.com/",
      role: "Developer"
    },
    {
      quote: "The sound device used is perfect. I can record this key device and receive the same message from my own site.",
      author: "Linda Brown",
      link: "https://www.linda-brown.com",
      role: "Audio Engineer"
    }
  ];

  return (
    <section className="py-16 bg-white">
      <div className="container mx-auto px-4">
        <h2 className="text-3xl font-bold text-center text-gray-800 mb-12">What Our Users Say</h2>
        <div className="grid md:grid-cols-3 gap-8">
          {testimonials.map((testimonial, index) => (
            <div key={index} className="bg-white p-6 rounded-lg border border-gray-200 shadow-sm">
              <div className="text-accent-400 mb-4">
                {[...Array(5)].map((_, i) => (
                  <span key={i}>★</span>
                ))}
              </div>
              <p className="text-gray-600 italic mb-4">"{testimonial.quote}"</p>
              <div>
                <a href={testimonial.link} className="text-primary-600 hover:underline font-medium">
                  {testimonial.author}
                </a>
                <p className="text-gray-500 text-sm">{testimonial.role}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Testimonials;