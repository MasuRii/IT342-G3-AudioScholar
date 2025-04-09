const HeroSection = () => {
    return (
      <section className="py-16 bg-gradient-to-r from-primary-50 to-primary-100">
        <div className="container mx-auto px-4 text-center">
          <h1 className="text-4xl md:text-5xl font-bold text-gray-800 mb-6">AudioScholar</h1>
          <p className="text-xl text-gray-600 max-w-2xl mx-auto">
            Transform your audio experience with AI-powered solutions
          </p>
          <button className="mt-8 bg-primary-500 hover:bg-primary-600 text-white font-medium py-3 px-8 rounded-md transition-colors shadow-md">
            Get Started
          </button>
        </div>
      </section>
    );
  };

  export default HeroSection;